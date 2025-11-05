# SozoTv Javadoc Generator
Write-Host "Javadoc yaratilmoqda..." -ForegroundColor Green
Remove-Item -Recurse -Force docs -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path docs | Out-Null

& javadoc -d docs -sourcepath app/src/main/java -subpackages com -author -version `
  -footer "<div style='text-align:center;padding:10px;font-family:Arial;font-size:14px;color:#555;border-top:1px solid #eee;margin-top:20px'><b>Author:</b> Azamov | <b>Version:</b> 2.3 | <b>Telegram:</b> <a href='https://t.me/saikou' target='_blank' style='color:#229ED9'>@saikou</a></div>" `
  -link https://developer.android.com/reference -quiet

if ($LASTEXITCODE -eq 0) {
  Write-Host "Tayyor! Ochilmoqda..." -ForegroundColor Green
  Start-Process "docs/index.html"
} else {
  Write-Host "Xato: .java fayllar topilmadi" -ForegroundColor Red
}
