# Self-Signed Code-Signing Certificate Guide

Use this guide to sign Simplified Billing installers for internal or controlled offline deployment.
It creates a private certificate authority (CA) and a code-signing certificate issued by that CA.

This does **not** make the installer publicly trusted. Every customer computer must be configured to
trust your private root certificate before Windows can identify the installer as a known publisher.
For public downloads or unmanaged customer computers, use a certificate from a public code-signing
certificate authority instead.

## What this creates

```text
Private Root CA (kept offline and secret)
    └── Internal Code-Signing Certificate (used on the release computer)
            └── Simplified Billing installer and application executables
```

The root certificate has two parts:

- a **private key**, which must never leave the secure release machine;
- a **public `.cer` file**, which is installed on approved customer computers.

## Prerequisites

- Windows PowerShell 5.1 or newer;
- administrator access on customer computers to install trust certificates;
- Windows SDK SignTool on the release computer;
- the built Simplified Billing installer;
- a protected local folder or password vault for certificate backups.

Install the Windows SDK if `signtool.exe` is unavailable. It is usually installed under:

```text
C:\Program Files (x86)\Windows Kits\10\bin\<SDK version>\x64\signtool.exe
```

## 1. Create the private root CA

Run this once on the secure release computer. Replace the organization name before running it.

```powershell
$certificateFolder = "C:\SimplifiedBillingSigning"
New-Item -ItemType Directory -Path $certificateFolder -Force | Out-Null

$rootCertificate = New-SelfSignedCertificate `
  -Type Custom `
  -Subject "CN=Simplified Billing Internal Root CA, O=Your Organization Name" `
  -KeyUsage CertSign, CRLSign, DigitalSignature `
  -KeyLength 4096 `
  -KeyExportPolicy Exportable `
  -HashAlgorithm SHA256 `
  -CertStoreLocation "Cert:\CurrentUser\My" `
  -NotAfter (Get-Date).AddYears(10) `
  -TextExtension @("2.5.29.19={text}CA=true&pathlength=1")

$rootCertificate.Thumbprint
```

Record the thumbprint in the release register. Export only the public root certificate for customer
computers:

```powershell
Export-Certificate `
  -Cert $rootCertificate `
  -FilePath "$certificateFolder\Simplified-Billing-Internal-Root-CA.cer"
```

Do not email, copy to USB, or upload the root private key. Export it only as an encrypted backup
stored in an approved password vault or offline secure location.

## 2. Create the code-signing certificate

Create a shorter-lived certificate used for signing releases:

```powershell
$codeSigningCertificate = New-SelfSignedCertificate `
  -Type Custom `
  -Subject "CN=Simplified Billing Internal Software Publisher, O=Your Organization Name" `
  -Signer $rootCertificate `
  -KeyUsage DigitalSignature `
  -KeySpec Signature `
  -KeyLength 3072 `
  -KeyExportPolicy Exportable `
  -HashAlgorithm SHA256 `
  -CertStoreLocation "Cert:\CurrentUser\My" `
  -NotAfter (Get-Date).AddYears(2) `
  -TextExtension @(
    "2.5.29.19={text}CA=false",
    "2.5.29.37={text}1.3.6.1.5.5.7.3.3"
  )

$codeSigningCertificate.Thumbprint

Export-Certificate `
  -Cert $codeSigningCertificate `
  -FilePath "$certificateFolder\Simplified-Billing-Internal-Publisher.cer"
```

`1.3.6.1.5.5.7.3.3` is the standard extended-key-usage identifier for code signing.

## 3. Back up the signing certificate securely

Keep the certificate in the Windows certificate store for normal signing. Create an encrypted PFX
backup only if your organization permits it:

```powershell
$pfxPassword = Read-Host "Enter a strong PFX backup password" -AsSecureString

Export-PfxCertificate `
  -Cert $codeSigningCertificate `
  -FilePath "$certificateFolder\Simplified-Billing-Internal-Publisher.pfx" `
  -Password $pfxPassword
```

Store the PFX and its password separately. Never put either in Git, the installer, a shared folder,
or a customer machine.

## 4. Trust the certificate on each customer computer

Copy these **public** files to the customer computer:

```text
Simplified-Billing-Internal-Root-CA.cer
Simplified-Billing-Internal-Publisher.cer
```

Open PowerShell as Administrator and install them:

```powershell
$certificateFolder = "C:\Path\To\CertificateFiles"

Import-Certificate `
  -FilePath "$certificateFolder\Simplified-Billing-Internal-Root-CA.cer" `
  -CertStoreLocation "Cert:\LocalMachine\Root"

Import-Certificate `
  -FilePath "$certificateFolder\Simplified-Billing-Internal-Publisher.cer" `
  -CertStoreLocation "Cert:\LocalMachine\TrustedPublisher"
```

For many managed computers, deploy the two public certificates by Active Directory Group Policy or
your device-management tool instead of manually importing them. Never deploy a `.pfx` or private
key to customer computers.

Verify the installed certificates:

```powershell
Get-ChildItem Cert:\LocalMachine\Root | Where-Object Subject -Like "*Simplified Billing Internal Root CA*"
Get-ChildItem Cert:\LocalMachine\TrustedPublisher | Where-Object Subject -Like "*Simplified Billing Internal Software Publisher*"
```

## 5. Build the installer

From the repository root, complete the release checks before signing:

```powershell
mvn -f backend\pom.xml clean verify
npm --prefix desktop ci
npm --prefix desktop run package:win
```

The expected installer is in:

```text
desktop\release\Simplified-Billing-<version>-x64.exe
```

## 6. Sign the installer and installed application executable

Find SignTool and set the code-signing thumbprint from step 2:

```powershell
$signTool = Get-ChildItem "C:\Program Files (x86)\Windows Kits\10\bin" `
  -Recurse -Filter signtool.exe | Select-Object -First 1 -ExpandProperty FullName

$certificateThumbprint = "PASTE-THE-CODE-SIGNING-THUMBPRINT-HERE"
$installer = Resolve-Path ".\desktop\release\Simplified-Billing-*-x64.exe"
$applicationExecutable = Resolve-Path ".\desktop\release\win-unpacked\Simplified Billing.exe"
```

Sign both files. The certificate is read from the release computer's certificate store, so no PFX
password is placed on the command line:

```powershell
& $signTool sign /sha1 $certificateThumbprint /fd SHA256 /v $applicationExecutable
& $signTool sign /sha1 $certificateThumbprint /fd SHA256 /v $installer
```

If the release computer has approved internet access, add the timestamp service supplied by your
organization or certificate provider:

```powershell
& $signTool sign /sha1 $certificateThumbprint /fd SHA256 `
  /tr "https://your-approved-timestamp-service" /td SHA256 /v $installer
```

For a fully offline release, omit `/tr`. The signature remains usable while the signing certificate
is valid. Renew and re-sign future installers before the two-year signing certificate expires.

## 7. Verify before distribution

On the release computer:

```powershell
& $signTool verify /pa /v $installer
Get-AuthenticodeSignature $installer | Format-List Status,StatusMessage,SignerCertificate
```

On a separate customer-style test computer where the two public certificates were imported:

1. Copy the signed installer.
2. Run `Get-AuthenticodeSignature` again.
3. Confirm `Status` is `Valid` and the publisher name is your internal software publisher.
4. Install the application and verify the signed application executable.
5. Test first startup, login, printing, backup, and restore.

Windows SmartScreen can still warn about a self-signed installer outside the managed trust group.
That is expected: the signature is private trust, not public reputation.

## Certificate rotation or compromise

Create a new code-signing certificate before the existing one expires, distribute its public
certificate to managed computers, and sign new releases with it. Keep the old public certificate
trusted only for as long as users need to verify old installers.

If the root or code-signing private key is lost or suspected compromised:

1. Stop signing immediately.
2. Create a new root CA and code-signing certificate.
3. Remove the old root/publisher certificates from managed customer certificate stores.
4. Deploy the new public certificates.
5. Rebuild and sign a new installer.
6. Record the incident and affected release versions.

## Security rules

- Use self-signed signing only for known, managed computers.
- Keep root private keys offline wherever possible.
- Restrict the signing certificate to release owners.
- Use a separate signing certificate from the root CA.
- Protect PFX backups with strong, separately stored passwords.
- Sign only after tests and release checks pass.
- Verify every signed installer before distribution.
- Do not use a self-signed certificate to claim public publisher trust.
