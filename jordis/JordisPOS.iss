#define MyAppName "JordisPOS"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "Jordis Technology"
#define MyAppExeName "JordisPOS.exe"

[Setup]
AppId={{8E9D9F7A-6B7B-4A6C-9F4C-7B9E2A1D5C30}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
SetupIconFile=JordisPOS.ico

DefaultDirName={autopf}\JordisPOS
DefaultGroupName={#MyAppName}

OutputDir=dist-inno
OutputBaseFilename=JordisPOS-Setup

Compression=lzma
SolidCompression=yes

ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible

PrivilegesRequired=admin

UninstallDisplayName={#MyAppName}
UninstallDisplayIcon={app}\{#MyAppExeName}

[Files]
Source: "app-image\JordisPOS\*"; DestDir: "{app}"; \
    Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autodesktop}\JordisPOS"; Filename: "{app}\{#MyAppExeName}"; \
    WorkingDir: "{app}"; IconFilename: "{app}\{#MyAppExeName}"
Name: "{autoprograms}\JordisPOS"; Filename: "{app}\{#MyAppExeName}"; \
    WorkingDir: "{app}"; IconFilename: "{app}\{#MyAppExeName}"

[Run]
Filename: "{app}\{#MyAppExeName}"; \
    Description: "Abrir JordisPOS"; \
    Flags: nowait postinstall skipifsilent