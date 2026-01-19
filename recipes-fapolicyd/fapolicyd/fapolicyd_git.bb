
SUMMARY = "A userspace daemon for application whitelisting"
DESCRIPTION = "fapolicyd is a userspace daemon that determines whether application execution is permitted based on a user-defined policy."
HOMEPAGE = "https://github.com/linux-application-whitelisting/fapolicyd"
LICENSE = "GPL-3.0-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=d32239bcb673463ab874e80d47fae504"

SRC_URI = "git://github.com/linux-application-whitelisting/fapolicyd;protocol=https;branch=main \
           file://0001-fix-dyn-linker-macro-for-cross-compile.patch \
           file://fapolicyd.init \
           file://fapolicyd.conf \
           file://00-allow-basic.rules \
           "

# Modify these as desired
PV = "1.4.3+git"
SRCREV = "d2d89b81d55d5815c4a612782e5247270a0ce067"

DEPENDS = " \
    autoconf \
    automake \
    libtool \
    eudev \
    openssl \
    file-native \
    file \
    libcap-ng \
    libseccomp \
    lmdb \
    uthash \
    python3 \
    rpm \
"
# Delete:
do_install[depends] += "file-native:do_populate_sysroot"



# Runtime dependencies - conditionally add systemd
RDEPENDS:${PN}:append = "${@'systemd' if d.getVar('FAPOLICYD_INIT_SYSTEM') == 'systemd' else ''}"


inherit autotools pkgconfig python3-dir useradd update-rc.d

S = "${WORKDIR}/git"
B = "${S}"

# Common paths: /lib/ld-linux-aarch64.so.1, /lib64/ld-linux-x86-64.so.2, /lib/ld-linux.so.3
EXTRA_OECONF = "--with-audit --disable-shared --with-system-ld-so=/lib64/ld-linux-x86-64.so.2"
CACHED_CONFIGUREVARS += "ac_cv_path_SYSTEM_LD_SO=/lib64/ld-linux-x86-64.so.2"


USERADD_PACKAGES = "${PN}"
GROUPADD_PARAM:${PN} = "-r fapolicyd"
USERADD_PARAM:${PN} = "-r -M -d ${localstatedir}/lib/fapolicyd -s ${base_sbindir}/nologin -g fapolicyd -c 'Application Whitelisting Daemon' fapolicyd"


# Init system configuration - set to "sysvinit" or "systemd"
FAPOLICYD_INIT_SYSTEM ?= "${INIT_MANAGER}"

# Sys V init script parameters (used if INIT_MANAGER is sysvinit)
INITSCRIPT_NAME = "fapolicyd"
INITSCRIPT_PARAMS = "start 99 2 3 4 5 . stop 99 0 1 6 ."

# Systemd service (used if INIT_MANAGER is systemd)
SYSTEMD_SERVICE:${PN} = "fapolicyd.service"


do_configure:prepend() {

    if ! echo "${PACKAGE_CLASSES}" | grep -qw "package_rpm"; then
        bbfatal "fapolicyd requires RPM backend (PACKAGE_CLASSES must be in 'package_rpm')"
    fi

    if [ $KERNEL_VERSION -lt "4.20" ]; then
        bbfatal "fapolicyd requires kernel version 4.20 or higher for FANOTIFY_OPEN_EXEC_PERM support."
    fi

    KERNEL_CONFIG="${STAGING_KERNEL_BUILDDIR}/.config"
    if [ ! -f "${KERNEL_CONFIG}" ]; then
        bbwarn "Kernel config not found; skipping FANOTIFY check."
    else
        if ! grep -q '^CONFIG_FANOTIFY=y' "${KERNEL_CONFIG}"; then
            bbfatal "Kernel missing CONFIG_FANOTIFY"
        fi

        if ! grep -q '^CONFIG_FANOTIFY_ACCESS_PERMISSIONS=y' "${KERNEL_CONFIG}"; then
            bbfatal "Kernel missing CONFIG_FANOTIFY_ACCESS_PERMISSIONS"
        fi
    fi

    cd ${S}
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

    # Install configuration files and other resources with proper permissions
    install -m 0755 -d ${D}${sysconfdir}/fapolicyd/rules.d
    install -m 0755 -d ${D}${sysconfdir}/fapolicyd/trust.d
    install -m 0755 -d ${D}${localstatedir}/lib/fapolicyd
    install -m 0755 -d ${D}${datadir}/fapolicyd
    install -m 0755 -d ${D}${libdir}/tmpfiles.d/
    install -m 0755 -d ${D}${runstatedir}/fapolicyd
    
    # Install common init files
    install -d ${D}${datadir}/bash-completion/completions
    install -m 0644 ${S}/init/fapolicyd.bash_completion ${D}${datadir}/bash-completion/completions/fapolicyd
    install -m 0644 ${WORKDIR}/fapolicyd.conf ${D}${sysconfdir}/fapolicyd/
    install -m 0644 ${S}/init/fapolicyd-tmpfiles.conf ${D}${libdir}/tmpfiles.d/fapolicyd.conf
    install -m 0644 ${S}/init/fapolicyd.trust ${D}${sysconfdir}/fapolicyd/trust.d/

    # Compile and install magic file

    ## Works but wrong compiler version!
    #rm -f ${S}/init/fapolicyd-magic.mgc
    #cd ${S}/init
    #file -C -m ${S}/init/fapolicyd-magic
    #if [ ! -f ${S}/init/fapolicyd-magic.mgc ]; then
    #    bbfatal "Failed to compile magic file"
    #fi
    #cd ${S}
    
    install -m 0644 ${S}/init/fapolicyd-magic ${D}${datadir}/fapolicyd/

    # Does not work!
    #ls -la ${D}${bindir}
    #${D}${bindir}/file -C -m ${D}${datadir}/fapolicyd/fapolicyd-magic

    # Does not work eather
    # install -m 0644 ${S}/init/fapolicyd-magic.mgc ${D}${datadir}/fapolicyd/
    
    # Install default rules files from upstream
    install -m 0644 ${WORKDIR}/00-allow-basic.rules ${D}${sysconfdir}/fapolicyd/rules.d/

    # Install init system files based on FAPOLICYD_INIT_SYSTEM
    case "${FAPOLICYD_INIT_SYSTEM}" in
        systemd)
            echo "Installing fapolicyd with systemd"
            install -m 0755 -d ${D}${systemd_unitdir}/system/
            install -m 0644 ${S}/init/fapolicyd.service ${D}${systemd_unitdir}/system/
            ;;
        sysvinit)
            echo "Installing fapolicyd with sysvinit"
            install -d ${D}${sysconfdir}/init.d
            install -m 0755 ${WORKDIR}/fapolicyd.init ${D}${sysconfdir}/init.d/fapolicyd
            ;;
        *)
            bberror "Invalid FAPOLICYD_INIT_SYSTEM value: ${FAPOLICYD_INIT_SYSTEM}"
            bberror "Must be: sysvinit or systemd"
            exit 1
            ;;
    esac

    # Clean up any stray top-level directory created by upstream install
    rmdir --ignore-fail-on-non-empty ${D}/fapolicyd || true
}

FILES:${PN} = " \
    ${bindir}/* \
    ${sbindir}/* \
    ${sysconfdir}/* \
    ${localstatedir}/* \
    ${libdir}/* \
    ${datadir}/* \
    ${systemd_unitdir}/system/* \
    ${sysconfdir}/init.d/* \
"