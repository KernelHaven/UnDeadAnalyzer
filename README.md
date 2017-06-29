# UnDeadAnalyzer

An analysis plug-in for [KernelHaven](https://github.com/KernelHaven/KernelHaven).
This analysis detects dead code blocks or Kconfig (`CONFIG_`) variables, which are either:
* defined in Kconfig but not used in code or build files
* used in code or build files, but not defined in Kconfig

## Capabilities

* Dead code analysis via: `net.ssehub.kernel_haven.default_analyses.DeadCodeAnalysis`
* Unused variables analysis via: `net.ssehub.kernel_haven.default_analyses.MissingAnalysis`

## Usage

To use this analysis, set `analysis.class` to `net.ssehub.kernel_haven.default_analyses.DeadCodeAnalysis` or `net.ssehub.kernel_haven.default_analyses.MissingAnalysis` in the KernelHaven properties.

### Dependencies

In addition to KernelHaven, this analysis has the following dependencies:
* [CnfUtils](https://github.com/KernelHaven/CnfUtils)

### Configuration

In addition to the default ones, this analysis has the following configuration options in the KernelHaven properties:

| Key | Mandatory | Default | Example | Description |
|-----|-----------|---------|---------|-------------|
| `analysis.missing.type` | No | `D` | `U` | Sets the type of missing analysis. `D` for "defined but not used", `U` for "used but not defined". Not case sensitive. |
