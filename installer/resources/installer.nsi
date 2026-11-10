!include MUI2.nsh

!define APP_NAME "Pairgoth"
!define COMP_NAME "FFG"
!define WEB_SITE "https://pairgoth.jeudego.org"
!define VERSION "@VERSION@"
!define COPYRIGHT "(c) FFG 2024"
!define DESCRIPTION "Go Pairing Software"
!define INSTALLER_NAME "target/install-pairgoth.exe"
!define MAIN_APP_EXE "pairgoth.bat"
!define MAIN_APP_ICO "pairgoth.ico"
!define ICON "resources/pairgoth.ico"
!define BANNER "resources/banner.bmp"
!define LICENSE_TXT "resources/LICENSE.txt"

!define INSTALL_DIR "$PROGRAMFILES64\${APP_NAME}"
!define INSTALL_TYPE "SetShellVarContext current"
!define DATA_DIR "$LocalAppdata\Pairgoth"
!define REG_ROOT "HKCU"
!define UNINSTALL_PATH "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}"
!define REG_START_MENU "Start Menu Folder"

var SM_Folder

######################################################################

VIProductVersion  "${VERSION}"
VIAddVersionKey "ProductName"  "${APP_NAME}"
VIAddVersionKey "CompanyName"  "${COMP_NAME}"
VIAddVersionKey "LegalCopyright"  "${COPYRIGHT}"
VIAddVersionKey "FileDescription"  "${DESCRIPTION}"
VIAddVersionKey "FileVersion"  "${VERSION}"

######################################################################

SetCompressor /SOLID Lzma
Name "${APP_NAME}"
Caption "${APP_NAME}"
OutFile "${INSTALLER_NAME}"
BrandingText "${APP_NAME}"
InstallDir "${INSTALL_DIR}"

######################################################################

!define MUI_ICON "${ICON}"
!define MUI_UNICON "${ICON}"
!define MUI_WELCOMEFINISHPAGE_BITMAP "${BANNER}"
!define MUI_UNWELCOMEFINISHPAGE_BITMAP "${BANNER}"

######################################################################

!include "MUI2.nsh"

!define MUI_ABORTWARNING
!define MUI_UNABORTWARNING

!insertmacro MUI_PAGE_WELCOME

!ifdef LICENSE_TXT
!insertmacro MUI_PAGE_LICENSE "${LICENSE_TXT}"
!endif

!ifdef REG_START_MENU
!define MUI_STARTMENUPAGE_DEFAULTFOLDER "${APP_NAME}"
!define MUI_STARTMENUPAGE_REGISTRY_ROOT "${REG_ROOT}"
!define MUI_STARTMENUPAGE_REGISTRY_KEY "${UNINSTALL_PATH}"
!define MUI_STARTMENUPAGE_REGISTRY_VALUENAME "${REG_START_MENU}"
!insertmacro MUI_PAGE_STARTMENU Application $SM_Folder
!endif

!insertmacro MUI_PAGE_INSTFILES

!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM

!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_UNPAGE_FINISH

!insertmacro MUI_LANGUAGE "English"

######################################################################

Section -MainProgram
	${INSTALL_TYPE}

	SetOverwrite ifnewer
	SetOutPath "$INSTDIR"
	File /r "target/files/"

	SetOutPath "${DATA_DIR}"
	File /r "target/data/"

	; the configuration lives with the data, where pairgoth.bat starts; never overwrite the user's own
	IfFileExists "${DATA_DIR}\pairgoth.properties" config_done
	IfFileExists "$INSTDIR\pairgoth.properties" 0 config_new
		; earlier installers wrote it in the installation directory
		ClearErrors
		CopyFiles /SILENT "$INSTDIR\pairgoth.properties" "${DATA_DIR}"
		IfErrors config_new
		Delete "$INSTDIR\pairgoth.properties"
		Goto config_done
	config_new:
		FileOpen $0 "${DATA_DIR}\pairgoth.properties" w
		FileWrite $0 "auth = none$\r$\n"
		FileClose $0
	config_done:
	SetOutPath "$INSTDIR"

SectionEnd

######################################################################

Section -Icons_Reg
SetOutPath "$INSTDIR"
WriteUninstaller "$INSTDIR\uninstall.exe"

!ifdef REG_START_MENU
!insertmacro MUI_STARTMENU_WRITE_BEGIN Application
CreateDirectory "$SMPROGRAMS\$SM_Folder"
CreateShortCut "$SMPROGRAMS\$SM_Folder\${APP_NAME}.lnk" "$INSTDIR\${MAIN_APP_EXE}" "" "$INSTDIR/${MAIN_APP_ICO}"
CreateShortCut "$DESKTOP\${APP_NAME}.lnk" "$INSTDIR\${MAIN_APP_EXE}" "" "$INSTDIR/${MAIN_APP_ICO}"
CreateShortCut "$SMPROGRAMS\$SM_Folder\Uninstall ${APP_NAME}.lnk" "$INSTDIR\uninstall.exe"

!ifdef WEB_SITE
WriteIniStr "$INSTDIR\${APP_NAME} website.url" "InternetShortcut" "URL" "${WEB_SITE}"
CreateShortCut "$SMPROGRAMS\$SM_Folder\${APP_NAME} Website.lnk" "$INSTDIR\${APP_NAME} website.url"
!endif
!insertmacro MUI_STARTMENU_WRITE_END
!endif

!ifndef REG_START_MENU
CreateDirectory "$SMPROGRAMS\${APP_NAME}"
CreateShortCut "$SMPROGRAMS\${APP_NAME}\${APP_NAME}.lnk" "$INSTDIR\${MAIN_APP_EXE}" "" "$INSTDIR/${MAIN_APP_ICO}"
CreateShortCut "$DESKTOP\${APP_NAME}.lnk" "$INSTDIR\${MAIN_APP_EXE}"
CreateShortCut "$SMPROGRAMS\${APP_NAME}\Uninstall ${APP_NAME}.lnk" "$INSTDIR\uninstall.exe"

!ifdef WEB_SITE
WriteIniStr "$INSTDIR\${APP_NAME} website.url" "InternetShortcut" "URL" "${WEB_SITE}"
CreateShortCut "$SMPROGRAMS\${APP_NAME}\${APP_NAME} Website.lnk" "$INSTDIR\${APP_NAME} website.url"
!endif
!endif

; earlier installers kept the java.exe path there and reinstalled next to it, inside the previous installation
DeleteRegKey ${REG_ROOT} "Software\Microsoft\Windows\CurrentVersion\App Paths\jre\bin\java.exe"
DeleteRegKey /ifempty ${REG_ROOT} "Software\Microsoft\Windows\CurrentVersion\App Paths\jre\bin"
DeleteRegKey /ifempty ${REG_ROOT} "Software\Microsoft\Windows\CurrentVersion\App Paths\jre"
WriteRegStr ${REG_ROOT} "${UNINSTALL_PATH}"  "DisplayName" "${APP_NAME}"
WriteRegStr ${REG_ROOT} "${UNINSTALL_PATH}"  "UninstallString" "$INSTDIR\uninstall.exe"
WriteRegStr ${REG_ROOT} "${UNINSTALL_PATH}"  "DisplayIcon" "$INSTDIR\${MAIN_APP_ICO}"
WriteRegStr ${REG_ROOT} "${UNINSTALL_PATH}"  "DisplayVersion" "${VERSION}"
WriteRegStr ${REG_ROOT} "${UNINSTALL_PATH}"  "Publisher" "${COMP_NAME}"

!ifdef WEB_SITE
WriteRegStr ${REG_ROOT} "${UNINSTALL_PATH}"  "URLInfoAbout" "${WEB_SITE}"
!endif
SectionEnd

######################################################################

; removes a directory's content, except any "tournamentfiles" folder and what leads to it:
; tournaments may have been stored under the installation directory
Function un.RemoveSparingTournaments
	Exch $R0 ; directory
	Push $R1 ; search handle
	Push $R2 ; entry
	FindFirst $R1 $R2 "$R0\*.*"
	loop:
		StrCmp $R2 "" done
		StrCmp $R2 "." next
		StrCmp $R2 ".." next
		IfFileExists "$R0\$R2\*.*" 0 file
			StrCmp $R2 "tournamentfiles" next
			Push "$R0\$R2"
			Call un.RemoveSparingTournaments
			RMDir "$R0\$R2" ; only when left empty
			Goto next
		file:
			Delete "$R0\$R2"
		next:
			FindNext $R1 $R2
			Goto loop
	done:
	FindClose $R1
	Pop $R2
	Pop $R1
	Pop $R0
FunctionEnd

######################################################################

Section Uninstall
${INSTALL_TYPE}

Push "$INSTDIR"
Call un.RemoveSparingTournaments
RMDir "$INSTDIR"

!ifdef REG_START_MENU
!insertmacro MUI_STARTMENU_GETFOLDER "Application" $SM_Folder
Delete "$SMPROGRAMS\$SM_Folder\${APP_NAME}.lnk"
Delete "$SMPROGRAMS\$SM_Folder\Uninstall ${APP_NAME}.lnk"
!ifdef WEB_SITE
Delete "$SMPROGRAMS\$SM_Folder\${APP_NAME} Website.lnk"
!endif
Delete "$DESKTOP\${APP_NAME}.lnk"

RmDir "$SMPROGRAMS\$SM_Folder"
!endif

!ifndef REG_START_MENU
Delete "$SMPROGRAMS\${APP_NAME}\${APP_NAME}.lnk"
Delete "$SMPROGRAMS\${APP_NAME}\Uninstall ${APP_NAME}.lnk"
!ifdef WEB_SITE
Delete "$SMPROGRAMS\${APP_NAME}\${APP_NAME} Website.lnk"
!endif
Delete "$DESKTOP\${APP_NAME}.lnk"

RmDir "$SMPROGRAMS\${APP_NAME}"
!endif

DeleteRegKey ${REG_ROOT} "${UNINSTALL_PATH}"
SectionEnd
