#!/usr/bin/env python3
"""Generates the AHTBooks.xcodeproj Xcode project file from the ios/AHTBooks source tree."""
import os
import pathlib
import hashlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
IOS_DIR = ROOT / "ios"
PROJECT_DIR = IOS_DIR / "AHTBooks.xcodeproj"
SRC_DIR = IOS_DIR / "AHTBooks"
UITEST_DIR = IOS_DIR / "AHTBooksUITests"

def make_uuid(seed: str) -> str:
    """Generate a stable 24-character hex PBX UUID from a string seed."""
    h = hashlib.sha1(seed.encode("utf-8")).hexdigest().upper()
    return h[:24]

def main():
    PROJECT_DIR.mkdir(parents=True, exist_ok=True)
    (PROJECT_DIR / "project.xcworkspace").mkdir(parents=True, exist_ok=True)
    (PROJECT_DIR / "xcshareddata" / "xcschemes").mkdir(parents=True, exist_ok=True)

    # Workspace data
    workspace_data = """<?xml version="1.0" encoding="UTF-8"?>
<Workspace
   version = "1.0">
   <FileRef
      location = "self:AHTBooks.xcodeproj">
   </FileRef>
</Workspace>
"""
    (PROJECT_DIR / "project.xcworkspace" / "contents.xcworkspacedata").write_text(workspace_data)

    # Collect Swift files and resources
    swift_files = sorted(list(SRC_DIR.glob("**/*.swift")))
    resource_dirs = [SRC_DIR / "Resources" / "Assets.xcassets"]

    proj_uuid = make_uuid("AHTBooks_Project")
    target_uuid = make_uuid("AHTBooks_Target")
    app_product_uuid = make_uuid("AHTBooks_Product_App")
    sources_phase_uuid = make_uuid("AHTBooks_Sources_Phase")
    resources_phase_uuid = make_uuid("AHTBooks_Resources_Phase")
    frameworks_phase_uuid = make_uuid("AHTBooks_Frameworks_Phase")
    config_list_proj_uuid = make_uuid("AHTBooks_ConfigList_Proj")
    config_list_target_uuid = make_uuid("AHTBooks_ConfigList_Target")
    main_group_uuid = make_uuid("AHTBooks_MainGroup")
    app_group_uuid = make_uuid("AHTBooks_AppGroup")

    build_files = []
    file_refs = []
    sources_entries = []
    resources_entries = []

    # Map files
    for sf in swift_files:
        rel_path = sf.relative_to(SRC_DIR)
        f_uuid = make_uuid(f"FILE_{rel_path}")
        bf_uuid = make_uuid(f"BUILDFILE_{rel_path}")
        file_refs.append(f'\t\t{f_uuid} /* {sf.name} */ = {{isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = "{sf.name}"; sourceTree = "<group>"; }};')
        build_files.append(f'\t\t{bf_uuid} /* {sf.name} in Sources */ = {{isa = PBXBuildFile; fileRef = {f_uuid} /* {sf.name} */; }};')
        sources_entries.append(f'\t\t\t\t{bf_uuid} /* {sf.name} in Sources */,')

    # Assets.xcassets
    assets_dir = SRC_DIR / "Resources" / "Assets.xcassets"
    if assets_dir.exists():
        assets_uuid = make_uuid("FILE_Assets.xcassets")
        assets_bf_uuid = make_uuid("BUILDFILE_Assets.xcassets")
        file_refs.append(f'\t\t{assets_uuid} /* Assets.xcassets */ = {{isa = PBXFileReference; lastKnownFileType = folder.assetcatalog; path = "Assets.xcassets"; sourceTree = "<group>"; }};')
        build_files.append(f'\t\t{assets_bf_uuid} /* Assets.xcassets in Resources */ = {{isa = PBXBuildFile; fileRef = {assets_uuid} /* Assets.xcassets */; }};')
        resources_entries.append(f'\t\t\t\t{assets_bf_uuid} /* Assets.xcassets in Resources */,')

    # Privacy manifest (App Store requires it for required-reason APIs such as UserDefaults)
    privacy_path = SRC_DIR / "Resources" / "PrivacyInfo.xcprivacy"
    privacy_uuid = make_uuid("FILE_PrivacyInfo.xcprivacy")
    if privacy_path.exists():
        privacy_bf_uuid = make_uuid("BUILDFILE_PrivacyInfo.xcprivacy")
        file_refs.append(f'\t\t{privacy_uuid} /* PrivacyInfo.xcprivacy */ = {{isa = PBXFileReference; lastKnownFileType = text.xml; path = "PrivacyInfo.xcprivacy"; sourceTree = "<group>"; }};')
        build_files.append(f'\t\t{privacy_bf_uuid} /* PrivacyInfo.xcprivacy in Resources */ = {{isa = PBXBuildFile; fileRef = {privacy_uuid} /* PrivacyInfo.xcprivacy */; }};')
        resources_entries.append(f'\t\t\t\t{privacy_bf_uuid} /* PrivacyInfo.xcprivacy in Resources */,')

    # Info.plist
    info_plist_path = SRC_DIR / "Info.plist"
    info_plist_uuid = make_uuid("FILE_Info.plist")
    file_refs.append(f'\t\t{info_plist_uuid} /* Info.plist */ = {{isa = PBXFileReference; lastKnownFileType = text.plist.xml; path = "Info.plist"; sourceTree = "<group>"; }};')

    # Product file ref
    file_refs.append(f'\t\t{app_product_uuid} /* AHTBooks.app */ = {{isa = PBXFileReference; explicitFileType = wrapper.application; includeInIndex = 0; path = AHTBooks.app; sourceTree = BUILT_PRODUCTS_DIR; }};')

    # UI test target (optional: only when ios/AHTBooksUITests exists)
    ui_files = sorted(UITEST_DIR.glob("*.swift")) if UITEST_DIR.exists() else []
    has_ui_tests = bool(ui_files)
    ui_target_uuid = make_uuid("AHTBooksUITests_Target")
    ui_product_uuid = make_uuid("AHTBooksUITests_Product")
    ui_sources_phase_uuid = make_uuid("AHTBooksUITests_Sources_Phase")
    ui_frameworks_phase_uuid = make_uuid("AHTBooksUITests_Frameworks_Phase")
    ui_config_list_uuid = make_uuid("AHTBooksUITests_ConfigList")
    ui_proxy_uuid = make_uuid("AHTBooksUITests_Proxy")
    ui_dependency_uuid = make_uuid("AHTBooksUITests_Dependency")
    ui_group_uuid = make_uuid("GROUP_AHTBooksUITests")
    ui_sources_entries = []
    ui_group_children = []
    for uf in ui_files:
        f_uuid = make_uuid(f"UITEST_FILE_{uf.name}")
        bf_uuid = make_uuid(f"UITEST_BUILDFILE_{uf.name}")
        file_refs.append(f'\t\t{f_uuid} /* {uf.name} */ = {{isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = "{uf.name}"; sourceTree = "<group>"; }};')
        build_files.append(f'\t\t{bf_uuid} /* {uf.name} in Sources */ = {{isa = PBXBuildFile; fileRef = {f_uuid} /* {uf.name} */; }};')
        ui_sources_entries.append(f'\t\t\t\t{bf_uuid} /* {uf.name} in Sources */,')
        ui_group_children.append(f'\t\t\t\t{f_uuid} /* {uf.name} */,')
    if has_ui_tests:
        file_refs.append(f'\t\t{ui_product_uuid} /* AHTBooksUITests.xctest */ = {{isa = PBXFileReference; explicitFileType = wrapper.cfbundle; includeInIndex = 0; path = AHTBooksUITests.xctest; sourceTree = BUILT_PRODUCTS_DIR; }};')

    ui_main_child = f"\t\t\t\t{ui_group_uuid} /* AHTBooksUITests */,\n" if has_ui_tests else ""
    ui_product_child = f"\t\t\t\t{ui_product_uuid} /* AHTBooksUITests.xctest */,\n" if has_ui_tests else ""
    ui_group_def = (f"""\t\t{ui_group_uuid} /* AHTBooksUITests */ = {{
\t\t\tisa = PBXGroup;
\t\t\tchildren = (
{chr(10).join(ui_group_children)}
\t\t\t);
\t\t\tpath = AHTBooksUITests;
\t\t\tsourceTree = "<group>";
\t\t}};
""" if has_ui_tests else "")
    ui_proxy_section = (f"""/* Begin PBXContainerItemProxy section */
\t\t{ui_proxy_uuid} /* PBXContainerItemProxy */ = {{
\t\t\tisa = PBXContainerItemProxy;
\t\t\tcontainerPortal = {proj_uuid} /* Project object */;
\t\t\tproxyType = 1;
\t\t\tremoteGlobalIDString = {target_uuid};
\t\t\tremoteInfo = AHTBooks;
\t\t}};
/* End PBXContainerItemProxy section */

""" if has_ui_tests else "")
    ui_frameworks_phase = (f"""\t\t{ui_frameworks_phase_uuid} /* Frameworks */ = {{
\t\t\tisa = PBXFrameworksBuildPhase;
\t\t\tbuildActionMask = 2147483647;
\t\t\tfiles = (
\t\t\t);
\t\t\trunOnlyForDeploymentPostprocessing = 0;
\t\t}};
""" if has_ui_tests else "")
    ui_native_target = (f"""\t\t{ui_target_uuid} /* AHTBooksUITests */ = {{
\t\t\tisa = PBXNativeTarget;
\t\t\tbuildConfigurationList = {ui_config_list_uuid} /* Build configuration list for PBXNativeTarget "AHTBooksUITests" */;
\t\t\tbuildPhases = (
\t\t\t\t{ui_sources_phase_uuid} /* Sources */,
\t\t\t\t{ui_frameworks_phase_uuid} /* Frameworks */,
\t\t\t);
\t\t\tbuildRules = (
\t\t\t);
\t\t\tdependencies = (
\t\t\t\t{ui_dependency_uuid} /* PBXTargetDependency */,
\t\t\t);
\t\t\tname = AHTBooksUITests;
\t\t\tproductName = AHTBooksUITests;
\t\t\tproductReference = {ui_product_uuid} /* AHTBooksUITests.xctest */;
\t\t\tproductType = "com.apple.product-type.bundle.ui-testing";
\t\t}};
""" if has_ui_tests else "")
    ui_target_attr = (f"""\t\t\t\t\t{ui_target_uuid} = {{
\t\t\t\t\t\tCreatedOnToolsVersion = 16.0;
\t\t\t\t\t\tTestTargetID = {target_uuid};
\t\t\t\t\t}};
""" if has_ui_tests else "")
    ui_target_ref = f"\t\t\t\t{ui_target_uuid} /* AHTBooksUITests */,\n" if has_ui_tests else ""
    ui_sources_phase = (f"""\t\t{ui_sources_phase_uuid} /* Sources */ = {{
\t\t\tisa = PBXSourcesBuildPhase;
\t\t\tbuildActionMask = 2147483647;
\t\t\tfiles = (
{chr(10).join(ui_sources_entries)}
\t\t\t);
\t\t\trunOnlyForDeploymentPostprocessing = 0;
\t\t}};
""" if has_ui_tests else "")
    ui_dependency_section = (f"""/* Begin PBXTargetDependency section */
\t\t{ui_dependency_uuid} /* PBXTargetDependency */ = {{
\t\t\tisa = PBXTargetDependency;
\t\t\ttarget = {target_uuid} /* AHTBooks */;
\t\t\ttargetProxy = {ui_proxy_uuid} /* PBXContainerItemProxy */;
\t\t}};
/* End PBXTargetDependency section */

""" if has_ui_tests else "")
    def ui_config(name, uuid_seed):
        return f"""\t\t{make_uuid(uuid_seed)} /* {name} */ = {{
\t\t\tisa = XCBuildConfiguration;
\t\t\tbuildSettings = {{
\t\t\t\tCODE_SIGN_STYLE = Automatic;
\t\t\t\tDEVELOPMENT_TEAM = 8PLDJ8ZVP5;
\t\t\t\tCURRENT_PROJECT_VERSION = 1;
\t\t\t\tGENERATE_INFOPLIST_FILE = YES;
\t\t\t\tIPHONEOS_DEPLOYMENT_TARGET = 17.0;
\t\t\t\tMARKETING_VERSION = 1.0.0;
\t\t\t\tPRODUCT_BUNDLE_IDENTIFIER = com.alhadaftech.bookflow.uitests;
\t\t\t\tPRODUCT_NAME = "$(TARGET_NAME)";
\t\t\t\tSWIFT_VERSION = 5.0;
\t\t\t\tTARGETED_DEVICE_FAMILY = "1,2";
\t\t\t\tTEST_TARGET_NAME = AHTBooks;
\t\t\t}};
\t\t\tname = {name};
\t\t}};
"""
    ui_configs = (ui_config("Debug", "CONF_UITEST_DEBUG") + ui_config("Release", "CONF_UITEST_RELEASE")) if has_ui_tests else ""
    ui_config_list = (f"""\t\t{ui_config_list_uuid} /* Build configuration list for PBXNativeTarget "AHTBooksUITests" */ = {{
\t\t\tisa = XCConfigurationList;
\t\t\tbuildConfigurations = (
\t\t\t\t{make_uuid("CONF_UITEST_DEBUG")} /* Debug */,
\t\t\t\t{make_uuid("CONF_UITEST_RELEASE")} /* Release */,
\t\t\t);
\t\t\tdefaultConfigurationIsVisible = 0;
\t\t\tdefaultConfigurationName = Release;
\t\t}};
""" if has_ui_tests else "")

    # Groups tree
    # Build hierarchical groups matching directory tree under AHTBooks
    def build_group(directory: pathlib.Path, group_name: str) -> tuple[str, list[str]]:
        g_uuid = make_uuid(f"GROUP_{directory.relative_to(IOS_DIR)}")
        children = []
        sub_groups = []
        
        entries = sorted(list(directory.iterdir()), key=lambda p: (not p.is_dir(), p.name))
        for item in entries:
            if item.name.startswith(".") or item.name == "Contents.json":
                continue
            if item.name == "Assets.xcassets":
                children.append(f'{make_uuid("FILE_Assets.xcassets")} /* Assets.xcassets */')
            elif item.name == "Info.plist":
                children.append(f'{info_plist_uuid} /* Info.plist */')
            elif item.name == "PrivacyInfo.xcprivacy":
                children.append(f'{privacy_uuid} /* PrivacyInfo.xcprivacy */')
            elif item.is_dir() and not item.name.endswith(".xcassets") and not item.name.endswith(".appiconset") and not item.name.endswith(".colorset"):
                sub_uuid, sub_defs = build_group(item, item.name)
                children.append(f'{sub_uuid} /* {item.name} */')
                sub_groups.extend(sub_defs)
            elif item.suffix == ".swift":
                rel = item.relative_to(SRC_DIR)
                f_uuid = make_uuid(f"FILE_{rel}")
                children.append(f'{f_uuid} /* {item.name} */')

        children_str = ",\n\t\t\t\t".join(children)
        if children:
            children_str = f"\n\t\t\t\t{children_str},\n\t\t\t"
        group_def = f"""\t\t{g_uuid} /* {group_name} */ = {{
\t\t\tisa = PBXGroup;
\t\t\tchildren = ({children_str});
\t\t\tpath = "{directory.name}";
\t\t\tsourceTree = "<group>";
\t\t}};"""
        sub_groups.append(group_def)
        return g_uuid, sub_groups

    src_root_uuid, all_groups = build_group(SRC_DIR, "AHTBooks")
    products_group_uuid = make_uuid("GROUP_Products")

    pbx_content = f"""// !$*UTF8*$!
{{
\tarchiveVersion = 1;
\tclasses = {{
\t}};
\tobjectVersion = 54;
\tobjects = {{

/* Begin PBXBuildFile section */
{chr(10).join(build_files)}
/* End PBXBuildFile section */

/* Begin PBXFileReference section */
{chr(10).join(file_refs)}
/* End PBXFileReference section */

{ui_proxy_section}/* Begin PBXFrameworksBuildPhase section */
\t\t{frameworks_phase_uuid} /* Frameworks */ = {{
\t\t\tisa = PBXFrameworksBuildPhase;
\t\t\tbuildActionMask = 2147483647;
\t\t\tfiles = (
\t\t\t);
\t\t\trunOnlyForDeploymentPostprocessing = 0;
\t\t}};
{ui_frameworks_phase}/* End PBXFrameworksBuildPhase section */

/* Begin PBXGroup section */
\t\t{main_group_uuid} = {{
\t\t\tisa = PBXGroup;
\t\t\tchildren = (
\t\t\t\t{src_root_uuid} /* AHTBooks */,
{ui_main_child}\t\t\t\t{products_group_uuid} /* Products */,
\t\t\t);
\t\t\tsourceTree = "<group>";
\t\t}};
\t\t{products_group_uuid} /* Products */ = {{
\t\t\tisa = PBXGroup;
\t\t\tchildren = (
\t\t\t\t{app_product_uuid} /* AHTBooks.app */,
{ui_product_child}\t\t\t);
\t\t\tname = Products;
\t\t\tsourceTree = "<group>";
\t\t}};
{chr(10).join(all_groups)}
{ui_group_def}/* End PBXGroup section */

/* Begin PBXNativeTarget section */
\t\t{target_uuid} /* AHTBooks */ = {{
\t\t\tisa = PBXNativeTarget;
\t\t\tbuildConfigurationList = {config_list_target_uuid} /* Build configuration list for PBXNativeTarget "AHTBooks" */;
\t\t\tbuildPhases = (
\t\t\t\t{sources_phase_uuid} /* Sources */,
\t\t\t\t{frameworks_phase_uuid} /* Frameworks */,
\t\t\t\t{resources_phase_uuid} /* Resources */,
\t\t\t);
\t\t\tbuildRules = (
\t\t\t);
\t\t\tdependencies = (
\t\t\t);
\t\t\tname = AHTBooks;
\t\t\tproductName = AHTBooks;
\t\t\tproductReference = {app_product_uuid} /* AHTBooks.app */;
\t\t\tproductType = "com.apple.product-type.application";
\t\t}};
{ui_native_target}/* End PBXNativeTarget section */

/* Begin PBXProject section */
\t\t{proj_uuid} /* Project object */ = {{
\t\t\tisa = PBXProject;
\t\t\tattributes = {{
\t\t\t\tBuildIndependentTargetsInParallel = 1;
\t\t\t\tLastUpgradeCheck = 1600;
\t\t\t\tTargetAttributes = {{
\t\t\t\t\t{target_uuid} = {{
\t\t\t\t\t\tCreatedOnToolsVersion = 16.0;
\t\t\t\t\t}};
{ui_target_attr}\t\t\t\t}};
\t\t\t}};
\t\t\tbuildConfigurationList = {config_list_proj_uuid} /* Build configuration list for PBXProject "AHTBooks" */;
\t\t\tcompatibilityVersion = "Xcode 14.0";
\t\t\tdevelopmentRegion = en;
\t\t\thasScannedForEncodings = 0;
\t\t\tknownRegions = (
\t\t\t\ten,
\t\t\t\tBase,
\t\t\t);
\t\t\tmainGroup = {main_group_uuid};
\t\t\tproductRefGroup = {products_group_uuid} /* Products */;
\t\t\tprojectDirPath = "";
\t\t\tprojectRoot = "";
\t\t\ttargets = (
\t\t\t\t{target_uuid} /* AHTBooks */,
{ui_target_ref}\t\t\t);
\t\t}};
/* End PBXProject section */

/* Begin PBXResourcesBuildPhase section */
\t\t{resources_phase_uuid} /* Resources */ = {{
\t\t\tisa = PBXResourcesBuildPhase;
\t\t\tbuildActionMask = 2147483647;
\t\t\tfiles = (
{chr(10).join(resources_entries)}
\t\t\t);
\t\t\trunOnlyForDeploymentPostprocessing = 0;
\t\t}};
/* End PBXResourcesBuildPhase section */

/* Begin PBXSourcesBuildPhase section */
\t\t{sources_phase_uuid} /* Sources */ = {{
\t\t\tisa = PBXSourcesBuildPhase;
\t\t\tbuildActionMask = 2147483647;
\t\t\tfiles = (
{chr(10).join(sources_entries)}
\t\t\t);
\t\t\trunOnlyForDeploymentPostprocessing = 0;
\t\t}};
{ui_sources_phase}/* End PBXSourcesBuildPhase section */
{ui_dependency_section}
/* Begin XCBuildConfiguration section */
\t\t{make_uuid("CONF_PROJ_DEBUG")} /* Debug */ = {{
\t\t\tisa = XCBuildConfiguration;
\t\t\tbuildSettings = {{
\t\t\t\tALWAYS_SEARCH_USER_PATHS = NO;
\t\t\t\tCLANG_ANALYZER_NONNULL = YES;
\t\t\t\tCLANG_CXX_LANGUAGE_STANDARD = "gnu++20";
\t\t\t\tCLANG_ENABLE_MODULES = YES;
\t\t\t\tCLANG_ENABLE_OBJC_ARC = YES;
\t\t\t\tCOPY_PHASE_STRIP = NO;
\t\t\t\tDEBUG_INFORMATION_FORMAT = dwarf;
\t\t\t\tENABLE_STRICT_OBJC_MSGSEND = YES;
\t\t\t\tENABLE_TESTABILITY = YES;
\t\t\t\tGCC_DYNAMIC_NO_PIC = NO;
\t\t\t\tGCC_OPTIMIZATION_LEVEL = 0;
\t\t\t\tGCC_PREPROCESSOR_DEFINITIONS = (
\t\t\t\t\t"DEBUG=1",
\t\t\t\t\t"$(inherited)",
\t\t\t\t);
\t\t\t\tIPHONEOS_DEPLOYMENT_TARGET = 17.0;
\t\t\t\tMTL_ENABLE_DEBUG_INFO = INCLUDE_SOURCE;
\t\t\t\tONLY_ACTIVE_ARCH = YES;
\t\t\t\tSDKROOT = iphoneos;
\t\t\t\tSWIFT_ACTIVE_COMPILATION_CONDITIONS = DEBUG;
\t\t\t\tSWIFT_OPTIMIZATION_LEVEL = "-Onone";
\t\t\t\tSWIFT_VERSION = 5.0;
\t\t\t}};
\t\t\tname = Debug;
\t\t}};
\t\t{make_uuid("CONF_PROJ_RELEASE")} /* Release */ = {{
\t\t\tisa = XCBuildConfiguration;
\t\t\tbuildSettings = {{
\t\t\t\tALWAYS_SEARCH_USER_PATHS = NO;
\t\t\t\tCLANG_ANALYZER_NONNULL = YES;
\t\t\t\tCLANG_CXX_LANGUAGE_STANDARD = "gnu++20";
\t\t\t\tCLANG_ENABLE_MODULES = YES;
\t\t\t\tCLANG_ENABLE_OBJC_ARC = YES;
\t\t\t\tCOPY_PHASE_STRIP = NO;
\t\t\t\tDEBUG_INFORMATION_FORMAT = "dwarf-with-dsym";
\t\t\t\tENABLE_NS_ASSERTIONS = NO;
\t\t\t\tENABLE_STRICT_OBJC_MSGSEND = YES;
\t\t\t\tGCC_OPTIMIZATION_LEVEL = s;
\t\t\t\tIPHONEOS_DEPLOYMENT_TARGET = 17.0;
\t\t\t\tMTL_ENABLE_DEBUG_INFO = NO;
\t\t\t\tSDKROOT = iphoneos;
\t\t\t\tSWIFT_COMPILATION_MODE = "wholemodule";
\t\t\t\tSWIFT_OPTIMIZATION_LEVEL = "-O";
\t\t\t\tSWIFT_VERSION = 5.0;
\t\t\t\tVALIDATE_PRODUCT = YES;
\t\t\t}};
\t\t\tname = Release;
\t\t}};
\t\t{make_uuid("CONF_TARGET_DEBUG")} /* Debug */ = {{
\t\t\tisa = XCBuildConfiguration;
\t\t\tbuildSettings = {{
\t\t\t\tASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;
\t\t\t\tASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME = AccentColor;
\t\t\t\tCODE_SIGN_STYLE = Automatic;
\t\t\t\tDEVELOPMENT_TEAM = 8PLDJ8ZVP5;
\t\t\t\tCURRENT_PROJECT_VERSION = 1;
\t\t\t\tGENERATE_INFOPLIST_FILE = NO;
\t\t\t\tINFOPLIST_FILE = AHTBooks/Info.plist;
\t\t\t\tIPHONEOS_DEPLOYMENT_TARGET = 17.0;
\t\t\t\tLD_RUNPATH_SEARCH_PATHS = (
\t\t\t\t\t"$(inherited)",
\t\t\t\t\t"@executable_path/Frameworks",
\t\t\t\t);
\t\t\t\tMARKETING_VERSION = 1.0.0;
\t\t\t\tPRODUCT_BUNDLE_IDENTIFIER = com.alhadaftech.bookflow;
\t\t\t\tPRODUCT_NAME = "$(TARGET_NAME)";
\t\t\t\tSWIFT_EMIT_LOC_STRINGS = YES;
\t\t\t\tSWIFT_VERSION = 5.0;
\t\t\t\tTARGETED_DEVICE_FAMILY = "1,2";
\t\t\t}};
\t\t\tname = Debug;
\t\t}};
\t\t{make_uuid("CONF_TARGET_RELEASE")} /* Release */ = {{
\t\t\tisa = XCBuildConfiguration;
\t\t\tbuildSettings = {{
\t\t\t\tASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;
\t\t\t\tASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME = AccentColor;
\t\t\t\tCODE_SIGN_STYLE = Automatic;
\t\t\t\tDEVELOPMENT_TEAM = 8PLDJ8ZVP5;
\t\t\t\tCURRENT_PROJECT_VERSION = 1;
\t\t\t\tGENERATE_INFOPLIST_FILE = NO;
\t\t\t\tINFOPLIST_FILE = AHTBooks/Info.plist;
\t\t\t\tIPHONEOS_DEPLOYMENT_TARGET = 17.0;
\t\t\t\tLD_RUNPATH_SEARCH_PATHS = (
\t\t\t\t\t"$(inherited)",
\t\t\t\t\t"@executable_path/Frameworks",
\t\t\t\t);
\t\t\t\tMARKETING_VERSION = 1.0.0;
\t\t\t\tPRODUCT_BUNDLE_IDENTIFIER = com.alhadaftech.bookflow;
\t\t\t\tPRODUCT_NAME = "$(TARGET_NAME)";
\t\t\t\tSWIFT_EMIT_LOC_STRINGS = YES;
\t\t\t\tSWIFT_VERSION = 5.0;
\t\t\t\tTARGETED_DEVICE_FAMILY = "1,2";
\t\t\t}};
\t\t\tname = Release;
\t\t}};
{ui_configs}/* End XCBuildConfiguration section */

/* Begin XCConfigurationList section */
\t\t{config_list_proj_uuid} /* Build configuration list for PBXProject "AHTBooks" */ = {{
\t\t\tisa = XCConfigurationList;
\t\t\tbuildConfigurations = (
\t\t\t\t{make_uuid("CONF_PROJ_DEBUG")} /* Debug */,
\t\t\t\t{make_uuid("CONF_PROJ_RELEASE")} /* Release */,
\t\t\t);
\t\t\tdefaultConfigurationIsVisible = 0;
\t\t\tdefaultConfigurationName = Release;
\t\t}};
\t\t{config_list_target_uuid} /* Build configuration list for PBXNativeTarget "AHTBooks" */ = {{
\t\t\tisa = XCConfigurationList;
\t\t\tbuildConfigurations = (
\t\t\t\t{make_uuid("CONF_TARGET_DEBUG")} /* Debug */,
\t\t\t\t{make_uuid("CONF_TARGET_RELEASE")} /* Release */,
\t\t\t);
\t\t\tdefaultConfigurationIsVisible = 0;
\t\t\tdefaultConfigurationName = Release;
\t\t}};
{ui_config_list}/* End XCConfigurationList section */

\t}};
\trootObject = {proj_uuid} /* Project object */;
}}
"""

    (PROJECT_DIR / "project.pbxproj").write_text(pbx_content)
    print(f"Generated {PROJECT_DIR / 'project.pbxproj'} with {len(swift_files)} Swift source files.")

    # Shared Scheme
    ui_testable = (f"""         <TestableReference
            skipped = "NO">
            <BuildableReference
               BuildableIdentifier = "primary"
               BlueprintIdentifier = "{ui_target_uuid}"
               BuildableName = "AHTBooksUITests.xctest"
               BlueprintName = "AHTBooksUITests"
               ReferencedContainer = "container:AHTBooks.xcodeproj">
            </BuildableReference>
         </TestableReference>
""" if has_ui_tests else "")
    scheme_content = f"""<?xml version="1.0" encoding="UTF-8"?>
<Scheme
   LastUpgradeVersion = "1600"
   version = "1.7">
   <BuildAction
      parallelizeBuildables = "YES"
      buildImplicitDependencies = "YES">
      <BuildActionEntries>
         <BuildActionEntry
            buildForTesting = "YES"
            buildForRunning = "YES"
            buildForProfiling = "YES"
            buildForArchiving = "YES"
            buildForAnalyzing = "YES">
            <BuildableReference
               BuildableIdentifier = "primary"
               BlueprintIdentifier = "{target_uuid}"
               BuildableName = "AHTBooks.app"
               BlueprintName = "AHTBooks"
               ReferencedContainer = "container:AHTBooks.xcodeproj">
            </BuildableReference>
         </BuildActionEntry>
      </BuildActionEntries>
   </BuildAction>
   <TestAction
      buildConfiguration = "Debug"
      selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB"
      selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB"
      shouldUseLaunchSchemeArgsEnv = "YES">
      <Testables>
{ui_testable}      </Testables>
   </TestAction>
   <LaunchAction
      buildConfiguration = "Debug"
      selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB"
      selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB"
      launchStyle = "0"
      useCustomWorkingDirectory = "NO"
      ignoresPersistentStateOnLaunch = "NO"
      debugDocumentVersioning = "YES"
      debugServiceExtension = "internal"
      allowLocationSimulation = "YES">
      <BuildableProductRunnable
         runnableDebuggingMode = "0">
         <BuildableReference
            BuildableIdentifier = "primary"
            BlueprintIdentifier = "{target_uuid}"
            BuildableName = "AHTBooks.app"
            BlueprintName = "AHTBooks"
            ReferencedContainer = "container:AHTBooks.xcodeproj">
         </BuildableReference>
      </BuildableProductRunnable>
   </LaunchAction>
   <ProfileAction
      buildConfiguration = "Release"
      shouldUseLaunchSchemeArgsEnv = "YES"
      savedToolIdentifier = ""
      useCustomWorkingDirectory = "NO"
      debugDocumentVersioning = "YES">
      <BuildableProductRunnable
         runnableDebuggingMode = "0">
         <BuildableReference
            BuildableIdentifier = "primary"
            BlueprintIdentifier = "{target_uuid}"
            BuildableName = "AHTBooks.app"
            BlueprintName = "AHTBooks"
            ReferencedContainer = "container:AHTBooks.xcodeproj">
         </BuildableReference>
      </BuildableProductRunnable>
   </ProfileAction>
   <AnalyzeAction
      buildConfiguration = "Debug">
   </AnalyzeAction>
   <ArchiveAction
      buildConfiguration = "Release"
      revealArchiveInOrganizer = "YES">
   </ArchiveAction>
</Scheme>
"""
    (PROJECT_DIR / "xcshareddata" / "xcschemes" / "AHTBooks.xcscheme").write_text(scheme_content)
    print(f"Generated scheme {PROJECT_DIR / 'xcshareddata' / 'xcschemes' / 'AHTBooks.xcscheme'}")

if __name__ == "__main__":
    main()
