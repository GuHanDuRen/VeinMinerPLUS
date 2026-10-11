param([string]$OutputPath = (Join-Path $PSScriptRoot 'veinminerplus-config.png'))

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

# Static preview of the 720 x 480 Java GUI, rendered at 2x for readability.
# System fonts approximate Minecraft's font; this is not an in-game screenshot.
$bitmap = [System.Drawing.Bitmap]::new(1440, 960)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.ScaleTransform(2, 2)
$graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$resources = [System.Collections.Generic.List[System.IDisposable]]::new()

function Brush([string]$Color) {
    $brush = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml($Color))
    $resources.Add($brush)
    return $brush
}

$background = Brush '#101820'
$panel = Brush '#18242D'
$border = Brush '#2B414C'
$header = Brush '#1D3039'
$accent = Brush '#59D8BC'
$text = Brush '#E5EEF2'
$muted = Brush '#96ADB7'
$inputFill = Brush '#101A22'
$button = Brush '#21333E'

$bodyFont = [System.Drawing.Font]::new('Microsoft YaHei UI', 9, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$titleFont = [System.Drawing.Font]::new('Microsoft YaHei UI', 11, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$smallFont = [System.Drawing.Font]::new('Microsoft YaHei UI', 8, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$numberFont = [System.Drawing.Font]::new('Consolas', 10, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
foreach ($font in @($bodyFont, $titleFont, $smallFont, $numberFont)) { $resources.Add($font) }

function Rect($Color, [float]$X, [float]$Y, [float]$Width, [float]$Height) {
    $graphics.FillRectangle($Color, $X, $Y, $Width, $Height)
}

function Text([string]$Value, [float]$X, [float]$Y, [float]$Width, [float]$Height, $Font = $bodyFont, $Color = $text, [switch]$Center) {
    $format = [System.Drawing.StringFormat]::new([System.Drawing.StringFormat]::GenericTypographic)
    $format.LineAlignment = [System.Drawing.StringAlignment]::Center
    $format.FormatFlags = [System.Drawing.StringFormatFlags]::NoWrap
    if ($Center) { $format.Alignment = [System.Drawing.StringAlignment]::Center }
    $graphics.DrawString($Value, $Font, $Color, [System.Drawing.RectangleF]::new($X, $Y, $Width, $Height), $format)
    $format.Dispose()
}

function Card([float]$Y, [float]$Height, [string]$Title) {
    Rect $border 80 $Y 560 $Height
    Rect $panel 81 ($Y + 1) 558 ($Height - 2)
    Rect $header 81 ($Y + 1) 558 15
    Rect $accent 81 ($Y + 1) 2 15
    Text $Title 92 ($Y + 2) 524 16 $bodyFont $accent
}

function InputBox([float]$X, [float]$Y, [float]$Width, [string]$Value) {
    Rect $border $X $Y $Width 20
    Rect $inputFill ($X + 1) ($Y + 1) ($Width - 2) 18
    Text $Value ($X + 4) $Y ($Width - 8) 20 $numberFont
}

function Field([float]$X, [float]$Y, [string]$Label, [string]$Value) {
    Text $Label ($X + 4) $Y 158 20
    InputBox ($X + 174) $Y 84 $Value
}

function Button([float]$X, [float]$Y, [float]$Width, [string]$Label, [switch]$Toggle, [switch]$Enabled, [switch]$Primary) {
    $fill = $button
    $labelColor = $text
    if ($Enabled) { $labelColor = $accent }
    if ($Primary) { $fill = $accent; $labelColor = $background }
    $buttonBorder = $border
    if ($Primary) { $buttonBorder = $accent }
    Rect $buttonBorder $X $Y $Width 20
    Rect $fill ($X + 1) ($Y + 1) ($Width - 2) 18
    if ($Toggle) {
        $stripe = $muted
        if ($Enabled) { $stripe = $accent }
        Rect $stripe ($X + 1) ($Y + 4) 2 12
    }
    Text $Label $X $Y $Width 20 $bodyFont $labelColor -Center
}

try {
    Rect $background 0 0 720 480
    Rect $background 80 6 560 34
    Rect $accent 80 6 3 34
    Text 'VeinMinerPlus 服务端配置' 92 8 536 17 $titleFont $text -Center
    Text '服务器端规则，修改后会影响所有玩家' 92 26 536 12 $smallFont $muted -Center

    Card 50 52 '普通 / 范围连锁'
    Field 92 70 '普通最大方块数' '1024'
    Field 366 70 '普通每 Tick 最大数' '32'

    Card 110 100 '爆破连锁'
    Field 92 130 '爆破最大方块数' '32767'
    Field 366 130 '爆破每 Tick 最大数' '64'
    Field 92 154 '爆破搜索距离' '48'
    Button 366 154 262 '曼哈顿搜索：开' -Toggle -Enabled
    Field 92 178 '爆破扫描区块/每 Tick' '8'

    Card 218 52 '性能保护'
    Field 92 238 '低 TPS 警告阈值' '15'
    Button 366 238 262 '低 TPS 自动降半径：开' -Toggle -Enabled

    Card 278 164 '玩家行为'
    Button 92 298 262 '消耗饱食度：关' -Toggle
    Button 366 298 262 '默认模式：普通连锁'
    Text '矿石爆破额外目标' 96 322 124 20
    InputBox 224 322 356 '*ore'
    Button 584 322 20 '-'
    Button 608 322 20 '+'
    InputBox 224 344 356 ''
    Button 584 344 20 '-'

    Rect $background 0 448 720 32
    Rect $border 80 448 560 1
    Button 198 454 104 '重置'
    Button 308 454 104 '完成' -Primary
    Button 418 454 104 '取消'

    # Annotation sits outside the GUI panel and is not part of the implementation.
    Text '静态效果预览' 649 50 68 13 $smallFont $muted
    Text '实际字体' 649 66 68 12 $smallFont $muted
    Text '以游戏为准' 649 78 68 12 $smallFont $muted

    $bitmap.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Output $OutputPath
} finally {
    foreach ($resource in $resources) { $resource.Dispose() }
    $graphics.Dispose()
    $bitmap.Dispose()
}
