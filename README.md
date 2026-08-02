# NCORDINA Field Tech

A mobile application for Android that enables company field technicians to create, complete, and share service reports while onsite at client locations.

## Features

- **Report Management**: Create, view, and manage field service reports
- **Client Information**: Capture client details (name, address, phone, email)
- **Job Types**: Support for three job types:
  - Service/Repair
  - Installation (On Loan)
  - Installation (Purchased)
- **Job Documentation**: Document work performed and findings
- **Photo Capture**: Add photos to reports (optional)
- **Digital Signatures**: Client signature capture with legal text
- **PDF Generation**: Generate professional PDF reports
- **File Sharing**: Share reports via email, WhatsApp, or other apps

## Technical Specifications

- **Platform**: Android (API Level 29+)
- **Framework**: Native Android with Jetpack Compose
- **Architecture**: MVVM (Model-View-ViewModel)
- **Database**: Room (SQLite)
- **PDF Generation**: iText 7
- **Navigation**: Navigation Compose
- **Storage**: Local file storage in `/Documents/reports/`

## Project Structure

```
app/src/main/java/com/example/fieldtechv20kc/
├── data/
│   ├── constants/
│   │   └── LegalText.kt
│   ├── database/
│   │   ├── dao/
│   │   ├── AppDatabase.kt
│   │   └── Converters.kt
│   ├── model/
│   │   ├── Client.kt
│   │   ├── JobType.kt
│   │   ├── Photo.kt
│   │   ├── Report.kt
│   │   └── ReportWithDetails.kt
│   └── repository/
│       └── ReportRepository.kt
├── navigation/
│   ├── AppNavigation.kt
│   └── Screen.kt
├── ui/
│   ├── components/
│   │   └── SignaturePad.kt
│   └── screens/
│       ├── ClientInfoScreen.kt
│       ├── HomeScreen.kt
│       ├── JobDocumentationScreen.kt
│       ├── JobTypeScreen.kt
│       ├── ReportDetailScreen.kt
│       └── SignatureScreen.kt
├── utils/
│   ├── FileSharing.kt
│   ├── PdfGenerator.kt
│   ├── PhotoCapture.kt
│   └── SignatureCapture.kt
├── viewmodel/
│   └── ReportViewModel.kt
├── FieldTechApplication.kt
└── MainActivity.kt
```

## Usage

1. **Create New Report**: Tap the + button on the home screen
2. **Enter Client Info**: Fill in client details (name, address, phone, email)
3. **Select Job Type**: Choose from Service/Repair, Installation (On Loan), or Installation (Purchased)
4. **Document Work**: Enter job description and findings, optionally add photos
5. **Client Signature**: Review legal text and capture client signature
6. **Generate Report**: PDF is automatically generated and saved
7. **Share Report**: Use the share button to send via email or other apps

## Legal Framework

The app includes job-type-specific legal text that must be reviewed and electronically signed by the client:

- **Service/Repair**: Service/Repair Authorisation & Acknowledgement
- **Installation (On Loan)**: Loan Installation Terms & Acknowledgement  
- **Installation (Purchased)**: Purchase Installation Terms & Acknowledgement

## Permissions

The app requires the following permissions:
- `WRITE_EXTERNAL_STORAGE`: Save PDF reports
- `READ_EXTERNAL_STORAGE`: Access saved reports
- `CAMERA`: Capture photos for reports
- `INTERNET`: Share reports (if needed)

## Building the App

1. Open the project in Android Studio
2. Sync the project with Gradle files
3. Build and run on an Android device or emulator (API Level 29+)

## Future Enhancements

- Cloud sync and backups
- Advanced photo editing/annotation
- Job scheduling & assignment integration
- Multi-language support
- iOS version





