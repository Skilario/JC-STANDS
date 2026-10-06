[Setup]
AppName=JC STANDS
AppVersion=1.0.1
DefaultDirName={autopf}\JC STANDS
DefaultGroupName=JC STANDS
OutputDir=..\INSTALADOR
OutputBaseFilename=JCSTANDS-Setup
Compression=lzma
SolidCompression=yes
CloseApplications=yes

[Files]
Source: "dist-exe\JCSTANDS\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\JC STANDS"; Filename: "{app}\JCSTANDS.exe"
Name: "{autodesktop}\JC STANDS"; Filename: "{app}\JCSTANDS.exe"

[Run]
Filename: "{app}\JCSTANDS.exe"; Description: "Abrir JC STANDS"; Flags: nowait postinstall skipifsilent
