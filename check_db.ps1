# Script kiem tra database dau gia
# Chay: .\check_db.ps1

$mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
$args_common = @("-u", "root", "-pKien2007zz@", "--default-character-set=utf8mb4", "auction_system")

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "   KIEM TRA DATABASE DAU GIA" -ForegroundColor Cyan  
Write-Host "========================================`n" -ForegroundColor Cyan

Write-Host "[1] Cac bang trong database:" -ForegroundColor Yellow
& $mysql @args_common -e "SHOW TABLES;" 2>$null

Write-Host "`n[2] Danh sach phien dau gia (auction_sessions):" -ForegroundColor Yellow
& $mysql @args_common -e "SELECT auction_id, item_id, seller_id, status, current_highest_bid, start_time, end_time FROM auction_sessions ORDER BY start_time DESC;" 2>$null

Write-Host "`n[3] Danh sach nguoi dung (users):" -ForegroundColor Yellow
& $mysql @args_common -e "SELECT id, username, role, email FROM users;" 2>$null

Write-Host "`n[4] Danh sach vat pham (items):" -ForegroundColor Yellow
& $mysql @args_common -e "SELECT id, name, category, init_price FROM items;" 2>$null

Write-Host "`n[5] Lich su dat gia (bid_transactions):" -ForegroundColor Yellow
& $mysql @args_common -e "SELECT * FROM bid_transactions ORDER BY bid_time DESC LIMIT 10;" 2>$null

Write-Host "`n========================================`n" -ForegroundColor Cyan
