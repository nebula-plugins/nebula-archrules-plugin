package com.netflix.nebula.archrules.gradle

import java.io.Serializable

fun not(predicate: ArchrulesPredicate): ArchrulesPredicate =
    ArchrulesPredicate.NotPredicate(predicate)

fun simpleName(name: String): ArchrulesPredicate =
    ArchrulesPredicate.SimpleNamePredicate(name)

sealed class ArchrulesPredicate : Serializable {

    abstract fun <R> accept(visitor: ArchrulesPredicateVisitor<R>): R

    class NotPredicate(val predicate: ArchrulesPredicate) : ArchrulesPredicate() {
        override fun <R> accept(visitor: ArchrulesPredicateVisitor<R>): R = visitor.visitNot(this)
    }

    class SimpleNamePredicate(val name: String) : ArchrulesPredicate() {
        override fun <R> accept(visitor: ArchrulesPredicateVisitor<R>): R = visitor.visitSimpleName(this)
    }

}

interface ArchrulesPredicateVisitor<R> {

    fun visitNot(predicate: ArchrulesPredicate.NotPredicate): R

    fun visitSimpleName(predicate: ArchrulesPredicate.SimpleNamePredicate): R

}
