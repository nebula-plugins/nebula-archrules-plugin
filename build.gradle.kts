plugins {
    id("com.netflix.nebula.root")
}
tasks.wrapper {
    distributionType = Wrapper.DistributionType.ALL // ALL helps when debugging gradle plugins
    gradleVersion = "9.5.0"
    distributionSha256Sum = "a3c4ba4aca8f0075688b9c5b18939fd28e8cb4357c227da5c1d9f38343791439"
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
