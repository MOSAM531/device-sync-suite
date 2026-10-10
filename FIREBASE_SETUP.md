# Firebase Realtime Database Setup (100% Free & Quick)

दोनों ऐप्स (App 1 और App 2) आपस में बिना किसी सर्वर कॉस्ट के रियल-टाइम में डेटा सिंक करने के लिए **Google Firebase Realtime Database** का इस्तेमाल करते हैं।

---

### Step 1: Firebase प्रोजेक्ट बनाएं (2 मिनट)
1. अपने ब्राउज़र में [Firebase Console](https://console.firebase.google.com/) खोलें।
2. **"Add project"** पर क्लिक करें और कोई भी नाम दें (जैसे: `MyDeviceSync`).
3. Google Analytics को बंद कर सकते हैं (Not needed) और **"Create Project"** दबाएं।

---

### Step 2: Realtime Database चालू करें
1. बायीं तरफ मेनू में **Build > Realtime Database** पर क्लिक करें।
2. **"Create Database"** बटन दबाएं।
3. Location में `United States (us-central1)` या `Singapore` चुनें और Next करें।
4. Security Rules में **"Start in test mode"** चुनें (ताकि दोनों ऐप्स बिना लॉगिन के तुरंत डेटा भेज और पढ़ सकें) और **Enable** पर क्लिक करें।

---

### Step 3: Database URL कॉपी करें
डेटाबेस बनने के बाद आपको सबसे ऊपर एक URL दिखेगा, जैसे:
`https://mydevicesync-default-rtdb.firebaseio.com/`

* इस URL को कॉपी कर लें!

---

### Step 4: Apps में URL कैसे डालें?
1. **App 2 (Target Phone):**
   - ऐप इंस्टॉल करके खोलें।
   - **Firebase Realtime Database URL** बॉक्स में यह URL पेस्ट करें।
   - **Save & Connect** दबाएं और फिर **Start Background Service** पर क्लिक करें।

2. **App 1 (Admin Controller):**
   - ऐप या वेब डैशबोर्ड में ऊपर **"Configure Firebase"** बटन दबाएं।
   - यही URL पेस्ट करके **Save & Connect** दबाएं।
   - आपका Admin डैशबोर्ड तुरंत target डिवाइस की लाइव लोकेशन, बैटरी और कंट्रोल्स दिखाना शुरू कर देगा!
