plugins {
    id("com.netflix.nebula.root")
}
tasks.wrapper {
    distributionType = Wrapper.DistributionType.ALL // ALL helps when debugging gradle plugins
    gradleVersion = "9.6.1"
    distributionSha256Sum = "61ba77b3ff7167e60962763eb4bae79db7120c189b9544358d0ade3c1e712a83"
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
