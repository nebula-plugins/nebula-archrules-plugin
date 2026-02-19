package com.tngtech.archunit.core.importer;

import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClassDescriptor;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.importer.resolvers.ClassResolver;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.ClassReader;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.tngtech.archunit.core.domain.JavaConstructor.CONSTRUCTOR_NAME;
import static java.util.stream.Collectors.toSet;

/**
 * copy of <a href="https://github.com/TNG/ArchUnit/blob/main/archunit/src/main/java/com/tngtech/archunit/core/importer/ClassFileProcessor.java">ClassFileProcessor</a>
 * with changes from <a href="https://github.com/TNG/ArchUnit/pull/1565">package dependency scanning</a> applied
 */
public class ClassFileProcessorWithPackage extends ClassFileProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(ClassFileProcessor.class);
    private final boolean md5InClassSourcesEnabled = ArchConfiguration.get().md5InClassSourcesEnabled();
    private final ClassResolver.Factory classResolverFactory = new ClassResolver.Factory();

    @Override
    JavaClasses process(ClassFileSource source) {
        ClassFileImportRecord importRecord = new ClassFileImportRecord();
        DependencyResolutionProcessWithPackage dependencyResolutionProcess = new DependencyResolutionProcessWithPackage();
        RecordAccessHandler accessHandler = new RecordAccessHandler(importRecord, dependencyResolutionProcess);
        ClassDetailsRecorder classDetailsRecorder = new ClassDetailsRecorder(importRecord, dependencyResolutionProcess);
        for (ClassFileLocation location : source) {
            try (InputStream s = location.openStream()) {
                JavaClassProcessor javaClassProcessor =
                        new JavaClassProcessor(new SourceDescriptor(location.getUri(), md5InClassSourcesEnabled), classDetailsRecorder, accessHandler);
                new ClassReader(s).accept(javaClassProcessor, 0);
                javaClassProcessor.createJavaClass().ifPresent(importRecord::add);
            } catch (Exception e) {
                LOG.warn(String.format("Couldn't import class from %s", location.getUri()), e);
            }
        }
        return new ClassGraphCreatorWithPackage(importRecord, dependencyResolutionProcess, getClassResolver(classDetailsRecorder)).complete();
    }
    private ClassResolver getClassResolver(ClassDetailsRecorder classDetailsRecorder) {
        ClassResolver classResolver = classResolverFactory.create();
        classResolver.setClassUriImporter(new UriImporterOfProcessor(classDetailsRecorder, md5InClassSourcesEnabled));
        return classResolver;
    }

    private static class UriImporterOfProcessor implements ClassResolver.ClassUriImporter {
        private final DeclarationHandler declarationHandler;
        private final boolean md5InClassSourcesEnabled;

        UriImporterOfProcessor(DeclarationHandler declarationHandler, boolean md5InClassSourcesEnabled) {
            this.declarationHandler = declarationHandler;
            this.md5InClassSourcesEnabled = md5InClassSourcesEnabled;
        }

        @Override
        public Optional<JavaClass> tryImport(URI uri) {
            try (InputStream inputStream = uri.toURL().openStream()) {
                JavaClassProcessor classProcessor = new JavaClassProcessor(new SourceDescriptor(uri, md5InClassSourcesEnabled), declarationHandler);
                new ClassReader(inputStream).accept(classProcessor, 0);
                return classProcessor.createJavaClass();
            } catch (Exception e) {
                LOG.warn(String.format("Error during import from %s, falling back to simple import", uri), e);
                return Optional.empty();
            }
        }
    }
    private static class ClassDetailsRecorder implements DeclarationHandler {
        private final ClassFileImportRecord importRecord;
        private final DependencyResolutionProcessWithPackage dependencyResolutionProcess;
        private String ownerName;

        private ClassDetailsRecorder(ClassFileImportRecord importRecord, DependencyResolutionProcessWithPackage dependencyResolutionProcess) {
            this.importRecord = importRecord;
            this.dependencyResolutionProcess = dependencyResolutionProcess;
        }

        @Override
        public boolean isNew(String className) {
            return !importRecord.getClasses().containsKey(className);
        }

        @Override
        public void onNewClass(String className, Optional<String> superclassName, List<String> interfaceNames) {
            ownerName = className;
            if (superclassName.isPresent()) {
                importRecord.setSuperclass(ownerName, superclassName.get());
                dependencyResolutionProcess.registerSupertype(superclassName.get());
            }
            importRecord.addInterfaces(ownerName, interfaceNames);
            dependencyResolutionProcess.registerSupertypes(interfaceNames);
            if(!className.endsWith(".package-info")) {
                if(className.contains(".")) {
                    String packageName = className.substring(0, className.lastIndexOf("."));
                    dependencyResolutionProcess.registerPackageInfo(packageName + ".package-info");
                }else {
                    dependencyResolutionProcess.registerPackageInfo("package-info");
                }
            }
        }

        @Override
        public void onDeclaredTypeParameters(DomainBuilders.JavaClassTypeParametersBuilder typeParametersBuilder) {
            importRecord.addTypeParameters(ownerName, typeParametersBuilder);
        }

        @Override
        public void onGenericSuperclass(DomainBuilders.JavaParameterizedTypeBuilder<JavaClass> genericSuperclassBuilder) {
            importRecord.addGenericSuperclass(ownerName, genericSuperclassBuilder);
        }

        @Override
        public void onGenericInterfaces(List<DomainBuilders.JavaParameterizedTypeBuilder<JavaClass>> genericInterfaceBuilders) {
            importRecord.addGenericInterfaces(ownerName, genericInterfaceBuilders);
        }

        @Override
        public void onDeclaredField(DomainBuilders.JavaFieldBuilder fieldBuilder, String fieldTypeName) {
            importRecord.addField(ownerName, fieldBuilder);
            dependencyResolutionProcess.registerMemberType(fieldTypeName);
        }

        @Override
        public void onDeclaredConstructor(DomainBuilders.JavaConstructorBuilder constructorBuilder, Collection<String> rawParameterTypeNames) {
            importRecord.addConstructor(ownerName, constructorBuilder);
            dependencyResolutionProcess.registerMemberTypes(rawParameterTypeNames);
        }

        @Override
        public void onDeclaredMethod(DomainBuilders.JavaMethodBuilder methodBuilder, Collection<String> rawParameterTypeNames, String rawReturnTypeName) {
            importRecord.addMethod(ownerName, methodBuilder);
            dependencyResolutionProcess.registerMemberTypes(rawParameterTypeNames);
            dependencyResolutionProcess.registerMemberType(rawReturnTypeName);
        }

        @Override
        public void onDeclaredStaticInitializer(DomainBuilders.JavaStaticInitializerBuilder staticInitializerBuilder) {
            importRecord.setStaticInitializer(ownerName, staticInitializerBuilder);
        }

        @Override
        public void onDeclaredClassAnnotations(Set<DomainBuilders.JavaAnnotationBuilder> annotationBuilders) {
            importRecord.addClassAnnotations(ownerName, annotationBuilders);
            registerAnnotationTypesToResolve(annotationBuilders);
        }

        @Override
        public void onDeclaredMemberAnnotations(String memberName, String descriptor, Set<DomainBuilders.JavaAnnotationBuilder> annotationBuilders) {
            importRecord.addMemberAnnotations(ownerName, memberName, descriptor, annotationBuilders);
            registerAnnotationTypesToResolve(annotationBuilders);
        }

        private void registerAnnotationTypesToResolve(Set<DomainBuilders.JavaAnnotationBuilder> annotationBuilders) {
            for (DomainBuilders.JavaAnnotationBuilder annotationBuilder : annotationBuilders) {
                dependencyResolutionProcess.registerAnnotationType(annotationBuilder.getFullyQualifiedClassName());
            }
        }

        @Override
        public void onDeclaredAnnotationValueType(String valueTypeName) {
            dependencyResolutionProcess.registerAnnotationType(valueTypeName);
        }

        @Override
        public void onDeclaredAnnotationDefaultValue(String methodName, String methodDescriptor, DomainBuilders.JavaAnnotationBuilder.ValueBuilder valueBuilder) {
            importRecord.addAnnotationDefaultValue(ownerName, methodName, methodDescriptor, valueBuilder);
        }

        @Override
        public void registerEnclosingClass(String ownerName, String enclosingClassName) {
            importRecord.setEnclosingClass(ownerName, enclosingClassName);
            dependencyResolutionProcess.registerEnclosingType(enclosingClassName);
        }

        @Override
        public void registerEnclosingCodeUnit(String ownerName, RawAccessRecord.CodeUnit enclosingCodeUnit) {
            importRecord.setEnclosingCodeUnit(ownerName, enclosingCodeUnit);
        }

        @Override
        public void onDeclaredClassObject(String typeName) {
            dependencyResolutionProcess.registerAccessToType(typeName);
        }

        @Override
        public void onDeclaredInstanceofCheck(String typeName) {
            dependencyResolutionProcess.registerAccessToType(typeName);
        }

        @Override
        public void onDeclaredThrowsClause(Collection<String> exceptionTypeNames) {
            dependencyResolutionProcess.registerMemberTypes(exceptionTypeNames);
        }

        @Override
        public void onDeclaredGenericSignatureType(String typeName) {
            dependencyResolutionProcess.registerGenericSignatureType(typeName);
        }
    }


    private static class RecordAccessHandler implements JavaClassProcessor.AccessHandler, TryCatchRecorder.TryCatchBlocksFinishedListener {
        private static final Logger LOG = LoggerFactory.getLogger(RecordAccessHandler.class);

        private final ClassFileImportRecord importRecord;
        private final DependencyResolutionProcessWithPackage dependencyResolutionProcess;
        private RawAccessRecord.CodeUnit codeUnit;
        private int lineNumber;
        private final TryCatchRecorder tryCatchRecorder = new TryCatchRecorder(this);

        private RecordAccessHandler(ClassFileImportRecord importRecord, DependencyResolutionProcessWithPackage dependencyResolutionProcess) {
            this.importRecord = importRecord;
            this.dependencyResolutionProcess = dependencyResolutionProcess;
        }

        @Override
        public void setContext(RawAccessRecord.CodeUnit codeUnit) {
            this.codeUnit = codeUnit;
        }

        @Override
        public void onLineNumber(int lineNumber, Label label) {
            this.lineNumber = lineNumber;
            tryCatchRecorder.onEncounteredLabel(label, lineNumber);
        }

        @Override
        public void onLabel(Label label) {
            tryCatchRecorder.onEncounteredLabel(label);
        }

        @Override
        public void handleFieldInstruction(int opcode, String owner, String name, String desc) {
            JavaFieldAccess.AccessType accessType = JavaFieldAccess.AccessType.forOpCode(opcode);
            LOG.trace("Found {} access to field {}.{}:{} in line {}", accessType, owner, name, desc, lineNumber);
            RawAccessRecord.TargetInfo target = new RawAccessRecord.TargetInfo(owner, name, desc);
            RawAccessRecord.ForField accessRecord = filled(new RawAccessRecord.ForField.Builder(), target)
                    .withAccessType(accessType)
                    .build();
            importRecord.registerFieldAccess(accessRecord);
            tryCatchRecorder.registerAccess(accessRecord);
            dependencyResolutionProcess.registerAccessToType(target.owner.getFullyQualifiedClassName());
        }

        @Override
        public void handleMethodInstruction(String owner, String name, String desc) {
            LOG.trace("Found call of method {}.{}:{} in line {}", owner, name, desc, lineNumber);
            RawAccessRecord.TargetInfo target = new RawAccessRecord.TargetInfo(owner, name, desc);
            RawAccessRecord accessRecord = filled(new RawAccessRecord.Builder(), target).build();
            if (CONSTRUCTOR_NAME.equals(name)) {
                importRecord.registerConstructorCall(accessRecord);
            } else {
                importRecord.registerMethodCall(accessRecord);
            }
            tryCatchRecorder.registerAccess(accessRecord);
            dependencyResolutionProcess.registerAccessToType(target.owner.getFullyQualifiedClassName());
        }

        @Override
        public void handleMethodReferenceInstruction(String owner, String name, String desc) {
            LOG.trace("Found method reference {}.{}:{} in line {}", owner, name, desc, lineNumber);
            RawAccessRecord.TargetInfo target = new RawAccessRecord.TargetInfo(owner, name, desc);
            RawAccessRecord accessRecord = filled(new RawAccessRecord.Builder(), target).build();
            if (CONSTRUCTOR_NAME.equals(name)) {
                importRecord.registerConstructorReference(accessRecord);
            } else {
                importRecord.registerMethodReference(accessRecord);
            }
            tryCatchRecorder.registerAccess(accessRecord);
            dependencyResolutionProcess.registerAccessToType(target.owner.getFullyQualifiedClassName());
        }

        @Override
        public void handleLambdaInstruction(String owner, String name, String desc) {
            RawAccessRecord.TargetInfo target = new RawAccessRecord.TargetInfo(owner, name, desc);
            importRecord.registerLambdaInvocation(filled(new RawAccessRecord.Builder(), target).build());
        }

        @Override
        public void handleReferencedClassObject(JavaClassDescriptor type, int lineNumber) {
            importRecord.registerReferencedClassObject(new RawReferencedClassObject.Builder()
                    .withOrigin(codeUnit)
                    .withTarget(type)
                    .withLineNumber(lineNumber)
                    .withDeclaredInLambda(false)
                    .build());
        }

        @Override
        public void handleInstanceofCheck(JavaClassDescriptor instanceOfCheckType, int lineNumber) {
            importRecord.registerInstanceofCheck(new RawInstanceofCheck.Builder()
                    .withOrigin(codeUnit)
                    .withTarget(instanceOfCheckType)
                    .withLineNumber(lineNumber)
                    .withDeclaredInLambda(false)
                    .build());
        }

        @Override
        public void handleTryCatchBlock(Label start, Label end, Label handler, JavaClassDescriptor throwableType) {
            LOG.trace("Found try/catch block between {} and {} for throwable {}", start, end, throwableType);
            tryCatchRecorder.registerTryCatchBlock(start, end, handler, throwableType);
        }

        @Override
        public void handleTryFinallyBlock(Label start, Label end, Label handler) {
            LOG.trace("Found try/finally block between {} and {}", start, end);
            tryCatchRecorder.registerTryFinallyBlock(start, end, handler);
        }

        @Override
        public void onMethodEnd() {
            tryCatchRecorder.onEncounteredMethodEnd();
        }

        @Override
        public void onTryCatchBlocksFinished(Set<RawTryCatchBlock.Builder> tryCatchBlocks) {
            tryCatchBlocks.forEach(it -> it.withDeclaringCodeUnit(codeUnit));
            importRecord.addTryCatchBlocks(tryCatchBlocks.stream().map(RawTryCatchBlock.Builder::build).collect(toSet()));
        }

        private <BUILDER extends RawAccessRecord.BaseBuilder<?, BUILDER>> BUILDER filled(BUILDER builder,
                                                                                         RawAccessRecord.TargetInfo target) {
            return builder
                    .withOrigin(codeUnit)
                    .withTarget(target)
                    .withLineNumber(lineNumber);
        }
    }
}
