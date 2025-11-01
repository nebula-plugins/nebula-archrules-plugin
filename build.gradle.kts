plugins {
    id("com.netflix.nebula.root")
}
tasks.wrapper {
    distributionType = Wrapper.DistributionType.ALL // ALL helps when debugging gradle plugins
    gradleVersion = "9.1.0"
    distributionSha256Sum = "b84e04fa845fecba48551f425957641074fcc00a88a84d2aae5808743b35fc85"
}
dependencyLocking {
    lockAllConfigurations()
}
contacts {
    addPerson("nebula-plugins-oss@netflix.com") {
        moniker = "Nebula Plugins Maintainers"
        github = "nebula-plugins"
    }
}