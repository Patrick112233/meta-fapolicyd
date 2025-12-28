# Recipe created by recipetool
# This is the basis of a recipe and may need further editing in order to be fully functional.
# (Feel free to remove these comments when editing.)

# WARNING: the following LICENSE and LIC_FILES_CHKSUM values are best guesses - it is
# your responsibility to verify that the values are complete and correct.
LICENSE = "GPL-3.0-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=d32239bcb673463ab874e80d47fae504"

SRC_URI = "git://github.com/linux-application-whitelisting/fapolicyd;protocol=https;branch=main"

# Modify these as desired
PV = "1.4.3+git"
SRCREV = "d2d89b81d55d5815c4a612782e5247270a0ce067"

S = "${WORKDIR}/git"

# NOTE: the following prog dependencies are unknown, ignoring: file
# NOTE: the following library dependencies are unknown, ignoring: crypto rpmio lmdb cap-ng md udev magic dpkg rpm seccomp
#       (this is based on recipes that have previously been built and packaged)

# NOTE: if this software is not capable of being built in a separate build directory
# from the source, you should replace autotools with autotools-brokensep in the
# inherit line
inherit autotools

# Specify any options you want to pass to the configure script using EXTRA_OECONF:
EXTRA_OECONF = ""

