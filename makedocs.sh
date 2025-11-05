cat > makedocs.sh << 'EOF'
#!/bin/bash
echo "Dokka bilan Javadoc-style HTML docs yaratilmoqda..."

# Tozalash
rm -rf docs
mkdir -p docs

# Dokka ishga tushirish
echo "Dokka ishga tushmoqda..."
./gradlew dokkaHtml --quiet

# Natijani docs papkaga ko'chirish
if [ -d "app/build/dokka/html" ]; then
    cp -r app/build/dokka/html/* docs/
    echo "TAYYOR! Ochilmoqda..."
    explorer.exe docs/index.html
else
    echo "XATO: app/build/dokka/html topilmadi"
    echo "Tekshiring: app/src/main/java/ ichida .kt fayllar borligini"
    echo "Yoki: ./gradlew dokkaHtml --info"
fi
EOF

chmod +x makedocs.sh