# STACKT 📱🏦

**STACKT** is an advanced Android financial management application designed to simplify expense tracking through automation. It combines AI-powered receipt scanning, geolocation-based spending insights, and a unique multi-user monitoring system.

---

## 🌟 Key Features

### 🔍 AI Receipt Scanner (OCR)
* **Automated Data Entry**: Uses the **TabScanner API** to scan physical receipts and extract the store name, transaction date, and total amount.
* **Smart Categorization**: Organizes expenses into pre-defined categories like *Food & Beverage*, *Technology*, and *Essential Goods*.
* **Camera Integration**: Supports direct camera capture and gallery uploads.

### 📍 Geolocation & Mapping
* **Spending Locations**: Integrated with **Google Maps SDK** to visualize where your money is being spent.
* **Proximity Alerts**: Uses `FusedLocationProvider` to track your location and provide real-time budget status when you are near shopping areas.
* **Interactive Markers**: Plot store locations directly on a map interface.

### 👥 Multi-Monitoring Module
* **Collaborative Finance**: Request permission to monitor another user's spending (ideal for families or partners).
* **Privacy First**: Features a secure **Request-Approval-Deny** workflow powered by Firebase.
* **Visual Analytics**: View a monitored user's data through dynamic **Pie Charts** (MPAndroidChart) and detailed transaction lists.

---

## 🛠️ Tech Stack

- **Language**: Java
- **Database**: Firebase Cloud Firestore (Real-time data)
- **Auth**: Firebase Authentication
- **APIs**: 
  - Google Maps & Places API
  - TabScanner OCR API
- **Libraries**:
  - `MPAndroidChart` (Data Visualization)
  - `OkHttp` (API Networking)
  - `Material Design` (UI Components)
