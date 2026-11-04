# Submission notes for metal miner 1.0.0

The primary upload file for **both sites** is `metal-miner-1.0.0+mc26.3.jar`. Upload the JAR itself as a Minecraft mod; the source JAR is an optional source attachment, and the gallery PNGs are project images. Do not upload this whole directory as a modpack.

Use `SUMMARY.txt` for the short summary, `PROJECT_DESCRIPTION.md` for the full description, `CHANGELOG.md` for the file changelog, and `icon.png` for the project icon. `basic.png` and `advanced.png` are unedited captures from the isolated Minecraft test client. The provided `metal-miner-1.0.0+mc26.3-sources.jar` contains this project's own Java, native bridge, build scripts and documentation; no game or third-party mod binaries are included.

## Shared version details

| Field | Value |
| --- | --- |
| Project name | metal miner |
| Project type | Minecraft mod |
| Release/version | 1.0.0+mc26.3 |
| Release channel | Release |
| Game version | Minecraft Java 26.3 only |
| Loader | Fabric only |
| Environment | Client only |
| Platform | macOS on Apple Silicon only |
| License | MIT |
| Required runtime dependencies | Iris 1.11.7 and Sodium 0.9.3-beta.1 for Fabric 26.3 |
| Optional in-game configuration UI | Mod Menu 21.0.0 or newer |
| Other requirements | Fabric Loader 0.19.5 or newer; Java 25 or newer |

Version support is intentionally narrow because this build uses rendering hooks specific to the listed game and mod versions. The JAR bundles an arm64 macOS native library for MetalFX. It does not bundle Iris, Sodium, Minecraft, or a shader pack. It is incompatible with Metal Upscaler because both mods alter the same render stage.

## Modrinth

Create one **mod** project with the title `metal miner` and, if available, slug `metal-miner`. Use the summary, description, icon and gallery images above. Mark the version **Fabric**, **Minecraft 26.3**, **client only**, and **Release**. Add Iris and Sodium as **required** project dependencies; add Mod Menu as **optional** if offered. Upload the main JAR as the primary file. The source JAR can be added separately as a **sources JAR**, which [Modrinth permits as an additional file](https://support.modrinth.com/en/articles/8793363-additional-files). Check that no loader or game version beyond the table is selected. [Modrinth's version API](https://docs.modrinth.com/api/operations/createversion/) documents these metadata fields.

This project was developed with substantial AI coding assistance, and the supplied summary, description and changelog are AI-assisted text. Select Modrinth's **AI-generated code** and **AI-generated text** disclosures. The icon was created by the project creator, and the screenshots come from a real game run. [Modrinth's disclosure guide](https://support.modrinth.com/en/articles/16567675-content-disclosures) explains the fields. [Its AI policy](https://support.modrinth.com/en/articles/16551575-disclosure-and-usage-of-ai) says a project made mainly through prompting and testing may be limited to unlisted visibility or rejected for public discovery; moderator review decides. The source archive supports inspection and further development but does not waive that policy.

## CurseForge

Create a **Minecraft → Mods** project named `metal miner`; **Performance** is a suitable category if offered. Choose MIT, add the supplied summary and description in the Markdown editor, and upload the icon and two images. Upload the main JAR under **Files** as a **Release**, selecting **26.3** and **Fabric**. Add [Iris Shaders](https://www.curseforge.com/minecraft/mc-mods/irisshaders) and [Sodium](https://www.curseforge.com/minecraft/mc-mods/sodium) as **Required Dependency** relations; Mod Menu may be **Optional**. Use the changelog above. [CurseForge's project guide](https://support.curseforge.com/support/solutions/articles/9000197241-creating-and-submitting-a-project) describes these fields and review.

The source JAR is supplied for sharing with developers. Upload the compiled mod JAR as the main CurseForge file; do not upload the source JAR as a second playable mod. Add a source link later if a public repository is created.
