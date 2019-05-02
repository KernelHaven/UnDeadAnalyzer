# UnDeadAnalyzer

![Build Status](https://jenkins-2.sse.uni-hildesheim.de/buildStatus/icon?job=KH_UnDeadAnalyzer)

An analysis plugin for [KernelHaven](https://github.com/KernelHaven/KernelHaven).

Analysis components for detecting dead code.

## Capabilities

* Detect dead code blocks (blocks that can never be selected via variability)
* Find variability variables which are either:
	* defined in variability model but not used in code or build files
	* used in code or build files, but not defined in variability model

## Usage

Place [`UnDeadAnalyzer.jar`](https://jenkins-2.sse.uni-hildesheim.de/job/KH_UnDeadAnalyzer/lastSuccessfulBuild/artifact/build/jar/UnDeadAnalyzer.jar) in the plugins folder of KernelHaven.

The following analysis components can be used as part of a `ConfiguredPipelineAnalysis`:
* `net.ssehub.kernel_haven.undead_analyzer.DeadCodeFinder` to find dead code blocks
* `net.ssehub.kernel_haven.undead_analyzer.MissingVariablesFinder` to find missing variables

Alternatively `analysis.class` can be set to one of
* `net.ssehub.kernel_haven.undead_analyzer.DeadCodeAnalysis` to run a dead code analysis


## Dependencies

In addition to KernelHaven, this plugin has the following dependencies:
* [CnfUtils](https://github.com/KernelHaven/CnfUtils)

## License

This plugin is licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0.html).
