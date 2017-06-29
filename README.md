# UnDeadAnalyzer
Analyzer plug-in to detect dead code blocks, or Kconfig (`CONFIG_`) variables,
which are either:
* defined in Kconfig but not used in code or build files
* used in code or build files, but not defined in Kconfig

## Provides
* Dead code analysis via: `net.ssehub.kernel_haven.default_analyses.DeadCodeAnalysis`
* Unused variables analysis via: `net.ssehub.kernel_haven.default_analyses.MissingAnalysis`

## Prerequisites
* [CnfUtils](https://github.com/KernelHaven/CnfUtils)
