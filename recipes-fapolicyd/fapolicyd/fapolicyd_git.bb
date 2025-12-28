
SUMMARY = "A userspace daemon for application whitelisting"
DESCRIPTION = "fapolicyd is a userspace daemon that determines whether application execution is permitted based on a user-defined policy."
HOMEPAGE = "https://github.com/linux-application-whitelisting/fapolicyd"
LICENSE = "GPL-3.0-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=d32239bcb673463ab874e80d47fae504"

SRC_URI = "git://github.com/linux-application-whitelisting/fapolicyd;protocol=https;branch=main"

# Modify these as desired
PV = "1.4.3+git"
SRCREV = "d2d89b81d55d5815c4a612782e5247270a0ce067"

DEPENDS = " \
    gcc \
    autoconf \
    automake \
    libtool \
    libudev \
    kernel-headers \
    systemd \
    openssl \
    file \
    libcap-ng \
    libseccomp \
    lmdb \
    uthash \
    python3 \
"
#    libgcrypt     rpm 
# TODO: check weather kernel >= 4.20 FANOTIFY_OPEN_EXEC_PERM must be available!

inherit autotools pkgconfig systemd python3-dir useradd

S = "${WORKDIR}/git"

# Specify any options you want to pass to the configure script using EXTRA_OECONF:
EXTRA_OECONF = "--with-audit --disable-shared"


USERADD_PACKAGES = "${PN}"
GROUPADD_PARAM:${PN} = "-r fapolicyd"
USERADD_PARAM:${PN} = "-r -M -d ${localstatedir}/lib/fapolicyd -s ${base_sbindir}/nologin -g fapolicyd -c 'Application Whitelisting Daemon' fapolicyd"

do_configure:prepend() {
    ./autogen.sh
}


do_install() {
    oe_runmake DESTDIR=${D} install

    # Substitute target-friendly interpreter paths in rules if placeholders exist
    if ls ${S}/rules.d/*.rules >/dev/null 2>&1; then
        sed -i "s|%python2_path%|${bindir}/python3|g" ${S}/rules.d/*.rules || true
        sed -i "s|%python3_path%|${bindir}/python3|g" ${S}/rules.d/*.rules || true
        # Intentionally avoid substituting %ld_so_path% with a host-derived value
    fi

    # Install configuration files and other resources
    install -m 0644 -d ${D}${sysconfdir}/fapolicyd/rules.d
    install -m 0644 -d ${D}${sysconfdir}/fapolicyd/trust.d
    install -m 0644 -d ${D}${localstatedir}/lib/fapolicyd
    install -m 0755 -d ${D}${datadir}/fapolicyd
    install -m 0644 -d ${D}${libdir}/tmpfiles.d/
    install -m 0755 -d ${D}${runstatedir}/fapolicyd
    
    # Init:
    install -d ${D}${datadir}/bash-completion/completions
    install -m 0644 ${S}/init/fapolicyd.bash_completion ${D}${datadir}/bash-completion/completions/fapolicyd
    install -m 0644 ${S}/init/fapolicyd.conf ${D}${sysconfdir}/fapolicyd/
    install -m 0644 ${S}/init/fapolicyd-magic ${D}${datadir}/fapolicyd/
    install -m 0644 ${S}/init/fapolicyd-magic.mgc ${D}${datadir}/fapolicyd/
    install -m 0644 ${S}/init/fapolicyd.service ${D}${systemd_unitdir}/system/
    install -m 0644 ${S}/init/fapolicyd-tmpfiles.conf ${D}${libdir}/tmpfiles.d/fapolicyd.conf
    install -m 0644 ${S}/init/fapolicyd.trust ${D}${sysconfdir}/fapolicyd/trust.d/
}

FILES_${PN} = " \
    ${bindir}/* \
    ${sbindir}/* \
    ${sysconfdir}/* \
    ${localstatedir}/* \
    ${libdir}/* \
    ${datadir}/* \
    ${systemd_unitdir}/system/* \
"

FILES_${PN}-dev += "${libdir}/*.a ${libdir}/*.la"
SYSTEMD_SERVICE_${PN} = "fapolicyd.service"