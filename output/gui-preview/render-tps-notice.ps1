param(
    [string]$OutputPath = (Join-Path $PSScriptRoot 'tps-notice.png')
)

Add-Type -AssemblyName System.Drawing

# Draw a neutral HUD schematic at 2x scale, using the Java HUD's logical coordinates.
$canvasWidth = 1000
$canvasHeight = 340
$uiScale = 2.0
$hudWidth = $canvasWidth / $uiScale
$hudHeight = $canvasHeight / $uiScale
$bitmap = [System.Drawing.Bitmap]::new($canvasWidth, $canvasHeight)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$graphics.Clear([System.Drawing.ColorTranslator]::FromHtml('#202830'))
$graphics.ScaleTransform($uiScale, $uiScale)

$hudFont = [System.Drawing.Font]::new('Microsoft YaHei UI', 9,
    [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$titleFont = [System.Drawing.Font]::new('Microsoft YaHei UI', 10,
    [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$mutedBrush = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#A3B4C1'))
$whiteBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::White)
$noticeBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 255, 198, 109))
$noticeBackground = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(192, 16, 24, 32))
$slotBackground = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#303A44'))
$barBackground = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#131A20'))
$slotPen = [System.Drawing.Pen]::new([System.Drawing.ColorTranslator]::FromHtml('#62717C'), 1)
$selectedPen = [System.Drawing.Pen]::new([System.Drawing.ColorTranslator]::FromHtml('#D8E3EA'), 1)
$format = [System.Drawing.StringFormat]::GenericTypographic.Clone()
$format.FormatFlags = $format.FormatFlags -bor [System.Drawing.StringFormatFlags]::MeasureTrailingSpaces

try {
    $heading = '触发后提示示意 · 非游戏截图'
    $headingSize = $graphics.MeasureString($heading, $titleFont, 1000, $format)
    $graphics.DrawString($heading, $titleFont, $mutedBrush,
        [single](($hudWidth - $headingSize.Width) / 2), [single]13, $format)

    $notice = 'TPS 过低（12.4），本次爆破范围已自动降低：48 → 24'
    $noticeWidth = [Math]::Min(520, $hudWidth - 24)
    $noticeSize = $graphics.MeasureString($notice, $hudFont, 1000, $format)
    if ($noticeSize.Width -gt $noticeWidth) {
        throw 'The example notice exceeds the HUD text width.'
    }
    $noticeX = ($hudWidth - $noticeSize.Width) / 2
    $noticeY = $hudHeight - 82
    # The top of the final text line follows height - 82, exactly as in the HUD code.
    $graphics.FillRectangle($noticeBackground, [single]($noticeX - 4), [single]($noticeY - 2),
        [single]($noticeSize.Width + 8), [single]($noticeSize.Height + 4))
    $graphics.DrawString($notice, $hudFont, $noticeBrush,
        [single]$noticeX, [single]$noticeY, $format)

    $counter = '连锁挖掘：128 个方块'
    $counterSize = $graphics.MeasureString($counter, $hudFont, 1000, $format)
    $graphics.DrawString($counter, $hudFont, $whiteBrush,
        [single](($hudWidth - $counterSize.Width) / 2), [single]($hudHeight - 59), $format)

    # Nine empty slots give a position reference without imitating a game scene.
    $slotSize = 20
    $barWidth = $slotSize * 9 + 2
    $barX = ($hudWidth - $barWidth) / 2
    $barY = $hudHeight - 22
    $graphics.FillRectangle($barBackground, [single]$barX, [single]$barY,
        [single]$barWidth, [single]22)
    for ($index = 0; $index -lt 9; $index++) {
        $slotX = $barX + 1 + $index * $slotSize
        $graphics.FillRectangle($slotBackground, [single]($slotX + 1), [single]($barY + 2),
            [single]18, [single]18)
        $graphics.DrawRectangle($slotPen, [single]($slotX + 0.5), [single]($barY + 1.5),
            [single]19, [single]19)
    }
    $graphics.DrawRectangle($selectedPen, [single]($barX + 0.5), [single]($barY + 0.5),
        [single]21, [single]21)

    $bitmap.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Output $OutputPath
}
finally {
    $format.Dispose()
    $selectedPen.Dispose()
    $slotPen.Dispose()
    $barBackground.Dispose()
    $slotBackground.Dispose()
    $noticeBackground.Dispose()
    $noticeBrush.Dispose()
    $whiteBrush.Dispose()
    $mutedBrush.Dispose()
    $titleFont.Dispose()
    $hudFont.Dispose()
    $graphics.Dispose()
    $bitmap.Dispose()
}
