# OAS Buddy

<img src="docs/branding/oas-buddy-icon.svg" width="88" align="left" alt="">

[![CI](https://github.com/maddingo/oas-buddy/actions/workflows/ci.yml/badge.svg)](https://github.com/maddingo/oas-buddy/actions/workflows/ci.yml)

A desktop editor for OpenAPI Specification (OAS) documents.

See [CLAUDE.md](CLAUDE.md) for the full project vision, MVP scope, and architecture decisions.

## Project Metrics

[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=oas-buddy_oas-buddy&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=oas-buddy_oas-buddy)

[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=oas-buddy_oas-buddy&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=oas-buddy_oas-buddy)

[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=oas-buddy_oas-buddy&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=oas-buddy_oas-buddy)

[![Security issues](https://sonarcloud.io/api/project_badges/measure?project=oas-buddy_oas-buddy&metric=software_quality_security_issues)](https://sonarcloud.io/summary/new_code?id=oas-buddy_oas-buddy)

## Branding

The app icon and its palette live in [docs/branding/](docs/branding/). `oas-buddy-icon.svg` is the
single hand-edited source; every other icon file is generated from it by `tools/generate-icons.sh`.

## Modules

- `oas-buddy-core` — document model, file I/O, and validation. No UI dependencies.
- `oas-buddy-desktop` — JavaFX desktop application.

## Requirements

- Java 25 and Maven, pinned via [mise](https://mise.jdx.dev/) (`mise install`).

## Build and run

```
mvn test                        # run all tests
cd oas-buddy-desktop
mvn javafx:run                  # run the desktop app
```

The `javafx:run` plugin prefix only resolves correctly when Maven is invoked from inside
`oas-buddy-desktop` itself; running it from the repo root via `-pl oas-buddy-desktop -am` fails
with "No plugin found for prefix 'javafx'".

**Running from an IDE:** run `no.maddin.oasbuddy.desktop.Launcher`, not `MainApp`, as the main
class. `MainApp` extends `javafx.application.Application` directly, and the JVM refuses to launch
a main class that does that from a plain classpath (no module-path) — even with the JavaFX jars
present — failing with "JavaFX runtime components are missing". `Launcher` is a plain class that
delegates to `MainApp`, which sidesteps that check.

## Packaging a native installer

```
cd oas-buddy-desktop
mvn -Djpackage package     # builds the installer(s) for whichever OS you're on
```

Produces, in `oas-buddy-desktop/target/dist/`:

| OS | Output |
|---|---|
| Linux | `.deb`, `.rpm`, and a portable app-image (own JVM, no installer) |
| macOS | `.pkg` |
| Windows | `.exe` |

The Linux app-image is for direct download/manual install, and what the Arch package is built
from (see below). The `package.yml` workflow tars it as `oas-buddy-linux-x86_64.tar.gz`.

Each installer bundles its own JVM, so the target machine doesn't need Java installed. This is
opt-in (`-Djpackage`) and only activates for the OS you're actually running on, so a plain
`mvn package` is unaffected.

### Arch Linux

On Arch and its derivatives (CachyOS, Manjaro, EndeavourOS, …) download the package attached to the
release, `oas-buddy-bin-X.Y.Z-1-x86_64.pkg.tar.zst`, and install the downloaded file:

```
curl -LO https://github.com/oas-buddy/oas-buddy/releases/download/vX.Y.Z/oas-buddy-bin-X.Y.Z-1-x86_64.pkg.tar.zst
sudo pacman -U ./oas-buddy-bin-X.Y.Z-1-x86_64.pkg.tar.zst
```

Download it first rather than handing pacman the URL: the package is not signed, and by default
pacman insists on a signature for a package it fetches itself (`RemoteFileSigLevel` falls back to
`SigLevel = Required`), failing with a 404 for the `.sig` file. A package file already on disk falls
under `LocalFileSigLevel = Optional` and installs unsigned, just like a package built from the AUR.

pacman does not update a package installed this way; install the next release the same way. The
package will also be published to the AUR as `oas-buddy-bin` (`yay -S oas-buddy-bin`), which does
update — it is not there yet, because AUR account registration is closed for the moment.

It repackages the release's app-image (own JVM, no Java dependency) into `/opt/oas-buddy`, adds the
application menu entry and icon, and puts `oas-buddy` on `PATH` as `/usr/bin/oas-buddy`. The
`PKGBUILD` lives in `oas-buddy-desktop/src/main/aur/`; the `package.yml` workflow builds the package
on every release and publishes the `PKGBUILD` to the AUR once that is set up. To build it by hand,
set `pkgver` in a copy of that directory to a released version, then:

```
updpkgsums && makepkg -si
```

### Running it from a terminal on Linux

The `.deb` and `.rpm` install to `/opt/oas-buddy`, and the launcher is `/opt/oas-buddy/bin/oas-buddy`.
The application menu entry is created for you; `/opt` is not on `PATH`, so for a command you can
type, link it yourself:

```
sudo ln -s /opt/oas-buddy/bin/oas-buddy /usr/local/bin/oas-buddy
```

`/usr/local/bin` is the right place for that — it is the FHS location for software the local
admin installs, and it comes before `/usr/bin` on a default `PATH`. The packages deliberately
don't create the link themselves: jpackage has no option for it, so it would mean vendoring
jpackage's own Debian maintainer scripts and RPM spec template just to add one symlink, and
Debian policy forbids a package writing into `/usr/local` in the first place. The same one-liner
works for the portable app-image, pointed at wherever you unpacked it.

Requires the OS-native packaging tool to be installed: `dpkg-deb`/`rpmbuild` on Linux, Xcode
command line tools on macOS, or the [WiX Toolset](https://wixtoolset.org/) on Windows — all
preinstalled on GitHub's hosted runners except `rpmbuild`. The `.github/workflows/package.yml`
workflow builds all four installers across an OS matrix; trigger it manually or by pushing a `v*`
tag.

## Releasing

Versions are CI-friendly (`${revision}`) — the POMs never carry a real
version, so there's nothing to bump in git. To cut a release, run the **Release** workflow
(Actions tab → Release → Run workflow) with a version like `1.2.0`. It tags `v1.2.0` and pushes
it; that tag push triggers `package.yml`, which builds the installers with that version baked in
and attaches them to the GitHub Release for `v1.2.0` (created automatically).
