; ============================================================================
;  IPR Safety Camera – NSIS Installer Script
;  Installs Client (Java) + Server (Python) with prerequisite auto-download
; ============================================================================

!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "FileFunc.nsh"
!include "nsDialogs.nsh"

; ── General settings ───────────────────────────────────────────────────────
Name "IPR Safety Camera"
OutFile "..\IPR-Safety-Camera-Setup.exe"
InstallDir "$PROGRAMFILES\IPR Safety Camera"
RequestExecutionLevel admin
BrandingText "IPR Safety Camera Installer"
Unicode True

; ── Variables ──────────────────────────────────────────────────────────────
Var ClientDir
Var ServerDir
Var JavaFound
Var PythonFound

; ── MUI settings ──────────────────────────────────────────────────────────
!define MUI_ICON "${NSISDIR}\Contrib\Graphics\Icons\modern-install.ico"
!define MUI_UNICON "${NSISDIR}\Contrib\Graphics\Icons\modern-uninstall.ico"

!define MUI_HEADERIMAGE
!define MUI_ABORTWARNING
!define MUI_WELCOMEFINISHPAGE_BITMAP "${NSISDIR}\Contrib\Graphics\Wizard\win.bmp"

; ── Welcome page ──────────────────────────────────────────────────────────
!define MUI_WELCOMEPAGE_TITLE "Welcome to IPR Safety Camera Setup"
!define MUI_WELCOMEPAGE_TEXT "This wizard will install the IPR Safety Camera system on your computer.$\r$\n$\r$\nThe installer will set up:$\r$\n  • Client Application (Java-based surveillance monitor)$\r$\n  • Server Application (Python AI backend)$\r$\n$\r$\nIf Java or Python are not detected, the installer will download and guide you through their installation.$\r$\n$\r$\nClick Next to continue."
!insertmacro MUI_PAGE_WELCOME

; ── Components page ───────────────────────────────────────────────────────
!insertmacro MUI_PAGE_COMPONENTS

; ── Client directory page ─────────────────────────────────────────────────
!define MUI_DIRECTORYPAGE_VARIABLE $ClientDir
!define MUI_PAGE_HEADER_TEXT "Client Install Location"
!define MUI_PAGE_HEADER_SUBTEXT "Choose the folder to install the Client application."
!define MUI_DIRECTORYPAGE_TEXT_TOP "The Client application will be installed in the following folder. Click Browse to select a different folder."
!insertmacro MUI_PAGE_DIRECTORY

; ── Server directory page ─────────────────────────────────────────────────
!define MUI_DIRECTORYPAGE_VARIABLE $ServerDir
!define MUI_PAGE_HEADER_TEXT "Server Install Location"
!define MUI_PAGE_HEADER_SUBTEXT "Choose the folder to install the Server application."
!define MUI_DIRECTORYPAGE_TEXT_TOP "The Server application will be installed in the following folder. Click Browse to select a different folder."
!insertmacro MUI_PAGE_DIRECTORY

; ── Install page ──────────────────────────────────────────────────────────
!insertmacro MUI_PAGE_INSTFILES

; ── Finish page ───────────────────────────────────────────────────────────
!define MUI_FINISHPAGE_RUN
!define MUI_FINISHPAGE_RUN_TEXT "Launch Client Application"
!define MUI_FINISHPAGE_RUN_FUNCTION "LaunchClient"
!insertmacro MUI_PAGE_FINISH

; ── Uninstaller pages ─────────────────────────────────────────────────────
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

; ── Language ──────────────────────────────────────────────────────────────
!insertmacro MUI_LANGUAGE "English"

; ============================================================================
;  INIT – set defaults
; ============================================================================
Function .onInit
    StrCpy $ClientDir "$PROGRAMFILES\IPR Safety Camera\Client"
    StrCpy $ServerDir "$PROGRAMFILES\IPR Safety Camera\Server"
    StrCpy $JavaFound "0"
    StrCpy $PythonFound "0"
FunctionEnd

; ============================================================================
;  SECTION: Prerequisites (always runs)
; ============================================================================
Section "Prerequisites (Java & Python)" SecPrereqs
    SectionIn RO ; required, cannot be unchecked

    ; ── Check Java ─────────────────────────────────────────────────────────
    DetailPrint "Checking for Java installation..."
    nsExec::ExecToStack 'cmd /c java -version 2>&1'
    Pop $0 ; return code
    Pop $1 ; output text
    ${If} $0 == 0
        DetailPrint "Java detected: $1"
        StrCpy $JavaFound "1"
    ${Else}
        DetailPrint "Java NOT found. Will download installer..."
        StrCpy $JavaFound "0"
    ${EndIf}

    ${If} $JavaFound == "0"
        DetailPrint ""
        DetailPrint "======================================"
        DetailPrint "  Downloading Java (Adoptium JDK 17)..."
        DetailPrint "======================================"
        DetailPrint ""
        NSISdl::download "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse" "$TEMP\jdk17_installer.msi"
        Pop $0
        ${If} $0 != "success"
            MessageBox MB_OK|MB_ICONEXCLAMATION "Failed to download Java: $0$\r$\n$\r$\nPlease install Java 17 manually from https://adoptium.net/"
            Goto java_done
        ${EndIf}

        DetailPrint "Download complete. Launching Java installer..."
        DetailPrint "Please follow the Java installation wizard."
        ExecWait 'msiexec /i "$TEMP\jdk17_installer.msi" ADDLOCAL=FeatureMain,FeatureEnvironment,FeatureJavaHome' $0
        ${If} $0 != 0
            MessageBox MB_OK|MB_ICONEXCLAMATION "Java installation returned code $0.$\r$\nPlease verify Java was installed correctly."
        ${Else}
            DetailPrint "Java installed successfully."
        ${EndIf}
        Delete "$TEMP\jdk17_installer.msi"
    ${EndIf}
    java_done:

    ; ── Check Python ───────────────────────────────────────────────────────
    DetailPrint ""
    DetailPrint "Checking for Python installation..."
    nsExec::ExecToStack 'cmd /c python --version 2>&1'
    Pop $0
    Pop $1
    ${If} $0 == 0
        DetailPrint "Python detected: $1"
        StrCpy $PythonFound "1"
    ${Else}
        ; Try "py" launcher
        nsExec::ExecToStack 'cmd /c py --version 2>&1'
        Pop $0
        Pop $1
        ${If} $0 == 0
            DetailPrint "Python detected via py launcher: $1"
            StrCpy $PythonFound "1"
        ${Else}
            DetailPrint "Python NOT found. Will download installer..."
            StrCpy $PythonFound "0"
        ${EndIf}
    ${EndIf}

    ${If} $PythonFound == "0"
        DetailPrint ""
        DetailPrint "======================================"
        DetailPrint "  Downloading Python 3.11.9..."
        DetailPrint "======================================"
        DetailPrint ""
        NSISdl::download "https://www.python.org/ftp/python/3.11.9/python-3.11.9-amd64.exe" "$TEMP\python_installer.exe"
        Pop $0
        ${If} $0 != "success"
            MessageBox MB_OK|MB_ICONEXCLAMATION "Failed to download Python: $0$\r$\n$\r$\nPlease install Python 3.11 manually from https://python.org/"
            Goto python_done
        ${EndIf}

        DetailPrint "Download complete. Launching Python installer..."
        DetailPrint "IMPORTANT: Check 'Add Python to PATH' in the installer!"
        MessageBox MB_OK|MB_ICONINFORMATION "The Python installer will now open.$\r$\n$\r$\nIMPORTANT: Please check the 'Add python.exe to PATH' checkbox at the bottom of the installer before clicking Install."
        ExecWait '"$TEMP\python_installer.exe" PrependPath=1' $0
        ${If} $0 != 0
            MessageBox MB_OK|MB_ICONEXCLAMATION "Python installation returned code $0.$\r$\nPlease verify Python was installed correctly."
        ${Else}
            DetailPrint "Python installed successfully."
        ${EndIf}
        Delete "$TEMP\python_installer.exe"
    ${EndIf}
    python_done:
SectionEnd

; ============================================================================
;  SECTION: Client Application
; ============================================================================
Section "Client Application" SecClient
    DetailPrint ""
    DetailPrint "Installing Client Application to $ClientDir ..."
    SetOutPath "$ClientDir"

    ; Copy client source files
    File /r "..\client\src"
    File "..\client\pom.xml"
    File "..\client\setup.bat"
    File "..\client\run.bat"

    DetailPrint "Client files installed."
SectionEnd

; ============================================================================
;  SECTION: Server Application
; ============================================================================
Section "Server Application" SecServer
    DetailPrint ""
    DetailPrint "Installing Server Application to $ServerDir ..."
    SetOutPath "$ServerDir"

    ; Copy server source files
    File "..\server\__init__.py"
    File "..\server\api_server.py"
    File "..\server\main.py"
    File "..\server\requirements.txt"
    File "..\server\test_model.py"
    File "..\server\train_agent.py"
    File "..\server\start_server.bat"

    ; Copy subdirectories
    File /r "..\server\models"
    File /r "..\server\safety_agent"

    DetailPrint "Server files installed."
SectionEnd

; ============================================================================
;  SECTION: Desktop Shortcuts
; ============================================================================
Section "Desktop Shortcuts" SecShortcuts
    DetailPrint "Creating desktop shortcuts..."

    ; Client shortcut
    SetOutPath "$ClientDir"
    CreateShortcut "$DESKTOP\IPR Safety Camera.lnk" \
        "$ClientDir\run.bat" "" "$ClientDir\run.bat" 0 \
        SW_SHOWNORMAL "" "Launch IPR Safety Camera"

    DetailPrint "Desktop shortcut created."
SectionEnd

; ============================================================================
;  SECTION: Uninstaller
; ============================================================================
Section "-CreateUninstaller"
    ; Write uninstaller
    WriteUninstaller "$INSTDIR\Uninstall.exe"

    ; Save install paths to registry for uninstaller
    WriteRegStr HKLM "Software\IPR Safety Camera" "ClientDir" "$ClientDir"
    WriteRegStr HKLM "Software\IPR Safety Camera" "ServerDir" "$ServerDir"
    WriteRegStr HKLM "Software\IPR Safety Camera" "InstallDir" "$INSTDIR"

    ; Add/Remove Programs entry
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\IPRSafetyCamera" \
        "DisplayName" "IPR Safety Camera"
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\IPRSafetyCamera" \
        "UninstallString" "$\"$INSTDIR\Uninstall.exe$\""
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\IPRSafetyCamera" \
        "Publisher" "IPR Team"
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\IPRSafetyCamera" \
        "DisplayVersion" "1.0"
SectionEnd

; ============================================================================
;  Component descriptions
; ============================================================================
!insertmacro MUI_FUNCTION_DESCRIPTION_BEGIN
    !insertmacro MUI_DESCRIPTION_TEXT ${SecPrereqs}  "Check and install Java 17 and Python 3.11 if not already present."
    !insertmacro MUI_DESCRIPTION_TEXT ${SecClient}    "Install the Java-based surveillance client with offline human detection."
    !insertmacro MUI_DESCRIPTION_TEXT ${SecServer}    "Install the Python AI server for advanced safety analysis."
    !insertmacro MUI_DESCRIPTION_TEXT ${SecShortcuts}  "Create desktop shortcuts to launch Client and Server."
!insertmacro MUI_FUNCTION_DESCRIPTION_END

; ============================================================================
;  Launch function (for Finish page)
; ============================================================================
Function LaunchClient
    ExecShell "open" "$ClientDir\run.bat"
FunctionEnd

; ============================================================================
;  UNINSTALLER
; ============================================================================
Section "Uninstall"
    ; Read saved paths from registry
    ReadRegStr $ClientDir HKLM "Software\IPR Safety Camera" "ClientDir"
    ReadRegStr $ServerDir HKLM "Software\IPR Safety Camera" "ServerDir"

    ; Remove client files
    ${If} $ClientDir != ""
        RMDir /r "$ClientDir"
    ${EndIf}

    ; Remove server files
    ${If} $ServerDir != ""
        RMDir /r "$ServerDir"
    ${EndIf}

    ; Remove desktop shortcut
    Delete "$DESKTOP\IPR Safety Camera.lnk"

    ; Remove uninstaller itself
    Delete "$INSTDIR\Uninstall.exe"
    RMDir "$INSTDIR"

    ; Clean up registry
    DeleteRegKey HKLM "Software\IPR Safety Camera"
    DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\IPRSafetyCamera"

    DetailPrint "Uninstallation complete."
SectionEnd
