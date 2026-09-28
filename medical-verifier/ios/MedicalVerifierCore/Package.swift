// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "MedicalVerifierCore",
    platforms: [
        .iOS(.v16)
    ],
    products: [
        .library(
            name: "MedicalVerifierCore",
            targets: ["MedicalVerifierCore"]
        )
    ],
    targets: [
        .target(
            name: "MedicalVerifierCore"
        ),
        .testTarget(
            name: "MedicalVerifierCoreTests",
            dependencies: ["MedicalVerifierCore"],
            resources: [
                .copy("Resources")
            ]
        )
    ]
)
