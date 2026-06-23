$ErrorActionPreference = 'Stop'

$workspace = 'D:\Engineering\PhotoProject'
$outputDoc = Join-Path $workspace 'docs\3.PhotoProject软件需求分析说明书.docx'
$figDir = Join-Path $workspace 'docs\requirements_figures'

$wdFormatXMLDocument = 12
$wdAlignCenter = 1
$wdAlignJustify = 3
$wdPageBreak = 7
$wdFieldEmpty = -1
$wdCollapseEnd = 0
$wdAutoFitWindow = 2

function Set-RangeFont($range, [string]$fontName = '宋体', [double]$size = 10.5, [int]$bold = 0) {
    $range.Font.Name = $fontName
    $range.Font.NameFarEast = $fontName
    $range.Font.Size = $size
    $range.Font.Bold = $bold
}

function Add-Paragraph($doc, [string]$text = '', [string]$style = '', [int]$align = 0, [double]$size = 10.5, [int]$bold = 0, [bool]$firstLine = $true, [string]$fontName = '宋体') {
    try {
        $range = $doc.Content
        $range.Collapse($wdCollapseEnd)
        if ($style -ne '') {
            $range.Style = $style
        }
        $range.Text = $text
        Set-RangeFont $range $fontName $size $bold
        $range.ParagraphFormat.Alignment = $align
        $range.ParagraphFormat.LineSpacingRule = 1
        $range.ParagraphFormat.LineSpacing = 18
        $range.ParagraphFormat.SpaceBefore = 0
        $range.ParagraphFormat.SpaceAfter = 6
        if ($firstLine) {
            $range.ParagraphFormat.FirstLineIndent = 21
        } else {
            $range.ParagraphFormat.FirstLineIndent = 0
        }
        $range.InsertParagraphAfter()
    } catch {
        Write-Output "Add-Paragraph failed: $text"
        throw
    }
}

function Add-Heading($doc, [string]$text, [int]$level) {
    $style = "标题 $level"
    $size = switch ($level) { 1 { 15 } 2 { 14 } default { 12 } }
    Add-Paragraph $doc $text $style 0 $size -1 $false
}

function Add-PageBreak($doc) {
    try {
        $range = $doc.Content
        $range.Collapse($wdCollapseEnd)
        $range.InsertBreak($wdPageBreak)
    } catch {
        Write-Output 'Add-PageBreak failed'
        throw
    }
}

function Add-Caption($doc, [string]$text) {
    Add-Paragraph $doc $text '' $wdAlignCenter 10.5 0 $false
}

function Add-Figure($doc, [string]$fileName, [string]$caption) {
    try {
        $path = Join-Path $figDir $fileName
        if (Test-Path -LiteralPath $path) {
            $range = $doc.Content
            $range.Collapse($wdCollapseEnd)
            $shape = $doc.InlineShapes.AddPicture($path, $false, $true, $range)
            if ($shape.Width -gt 430) {
                $ratio = 430 / $shape.Width
                $shape.Width = 430
                $shape.Height = $shape.Height * $ratio
            }
            $shape.Range.ParagraphFormat.Alignment = $wdAlignCenter
            $shape.Range.InsertParagraphAfter()
        }
        Add-Caption $doc $caption
    } catch {
        Write-Output "Add-Figure failed: $fileName"
        throw
    }
}

function Add-Table($doc, [array]$headers, [array]$rows) {
    try {
        $range = $doc.Content
        $range.Collapse($wdCollapseEnd)
        $table = $doc.Tables.Add($range, $rows.Count + 1, $headers.Count)
        $table.Borders.Enable = 1
        $table.Range.Font.Name = '宋体'
        $table.Range.Font.NameFarEast = '宋体'
        $table.Range.Font.Size = 10
        for ($c = 1; $c -le $headers.Count; $c++) {
            $table.Cell(1, $c).Range.Text = [string]$headers[$c - 1]
            $table.Cell(1, $c).Range.Font.Bold = -1
            $table.Cell(1, $c).Range.ParagraphFormat.Alignment = $wdAlignCenter
        }
        for ($r = 0; $r -lt $rows.Count; $r++) {
            for ($c = 0; $c -lt $headers.Count; $c++) {
                $table.Cell($r + 2, $c + 1).Range.Text = [string]$rows[$r][$c]
            }
        }
        $table.AutoFitBehavior($wdAutoFitWindow)
        $doc.Content.InsertParagraphAfter()
    } catch {
        Write-Output "Add-Table failed: $($headers -join ',')"
        throw
    }
}

$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0

try {
    $doc = $word.Documents.Add()

    $doc.PageSetup.TopMargin = 72
    $doc.PageSetup.BottomMargin = 72
    $doc.PageSetup.LeftMargin = 90
    $doc.PageSetup.RightMargin = 90
    $doc.PageSetup.HeaderDistance = 42
    $doc.PageSetup.FooterDistance = 50

    $doc.Styles.Item('正文').Font.Name = '宋体'
    $doc.Styles.Item('正文').Font.NameFarEast = '宋体'
    $doc.Styles.Item('正文').Font.Size = 10.5
    $doc.Styles.Item('标题 1').Font.NameFarEast = '宋体'
    $doc.Styles.Item('标题 1').Font.Size = 15
    $doc.Styles.Item('标题 1').Font.Bold = -1
    $doc.Styles.Item('标题 2').Font.NameFarEast = '宋体'
    $doc.Styles.Item('标题 2').Font.Size = 14
    $doc.Styles.Item('标题 2').Font.Bold = -1
    $doc.Styles.Item('标题 3').Font.NameFarEast = '宋体'
    $doc.Styles.Item('标题 3').Font.Size = 12
    $doc.Styles.Item('标题 3').Font.Bold = -1

    Add-Paragraph $doc '《软件需求分析说明书》' '' $wdAlignCenter 26 0 $false
    Add-Paragraph $doc '老照片修复系统的设计与实现' '' $wdAlignCenter 26 0 $false
    Add-Paragraph $doc '' '' 0 15 0 $false
    Add-Paragraph $doc '项 目 名 称： 老照片修复系统的设计与实现          ' '' 0 15 0 $false
    Add-Paragraph $doc '成 员 名 单：          ' '' 0 15 0 $false
    Add-Paragraph $doc '导       师：          ' '' 0 15 0 $false
    Add-Paragraph $doc '工 程 领 域：         Web 应用开发与人工智能图像处理          ' '' 0 15 0 $false
    Add-Paragraph $doc '研 究 方 向：         老照片智能修复与相册管理          ' '' 0 15 0 $false
    Add-Paragraph $doc '' '' 0 15 0 $false
    Add-Paragraph $doc '' '' 0 15 0 $false
    Add-Paragraph $doc '中国科学技术大学软件学院' '' $wdAlignCenter 15 0 $false '楷体_GB2312'
    Add-PageBreak $doc

    Add-Paragraph $doc '目  录' '' $wdAlignCenter 16 -1 $false
    Add-Paragraph $doc '1 引言' '' 0 10.5 0 $false
    Add-Paragraph $doc '  1.1 目的' '' 0 10.5 0 $false
    Add-Paragraph $doc '  1.2 背景' '' 0 10.5 0 $false
    Add-Paragraph $doc '  1.3 系统现状与发展' '' 0 10.5 0 $false
    Add-Paragraph $doc '2 需求概述' '' 0 10.5 0 $false
    Add-Paragraph $doc '  2.1 目标' '' 0 10.5 0 $false
    Add-Paragraph $doc '  2.2 用户特点' '' 0 10.5 0 $false
    Add-Paragraph $doc '  2.3 研究内容' '' 0 10.5 0 $false
    Add-Paragraph $doc '3 功能需求' '' 0 10.5 0 $false
    Add-Paragraph $doc '  3.1 课题内容' '' 0 10.5 0 $false
    Add-Paragraph $doc '  3.2 系统需求分析' '' 0 10.5 0 $false
    Add-Paragraph $doc '  3.3 功能模块图' '' 0 10.5 0 $false
    Add-Paragraph $doc '  3.4 系统模块需求说明' '' 0 10.5 0 $false
    Add-Paragraph $doc '  3.5 主要接口需求' '' 0 10.5 0 $false
    Add-Paragraph $doc '  3.6 数据需求' '' 0 10.5 0 $false
    Add-Paragraph $doc '4 非功能需求' '' 0 10.5 0 $false
    Add-Paragraph $doc '  4.1 性能需求' '' 0 10.5 0 $false
    Add-Paragraph $doc '  4.2 运行环境需求' '' 0 10.5 0 $false
    Add-Paragraph $doc '  4.3 安全性需求' '' 0 10.5 0 $false
    Add-Paragraph $doc '  4.4 可维护性需求' '' 0 10.5 0 $false
    Add-Paragraph $doc '  4.5 可靠性需求' '' 0 10.5 0 $false
    Add-Paragraph $doc '  4.6 易用性需求' '' 0 10.5 0 $false
    Add-Paragraph $doc '5 验收标准' '' 0 10.5 0 $false
    Add-PageBreak $doc

    Add-Heading $doc '1 引言' 1
    Add-Heading $doc '1.1 目的' 2
    Add-Paragraph $doc '本软件需求分析说明书用于明确 PhotoProject 项目中“老照片修复系统”的建设目标、用户范围、业务流程、功能需求、运行环境需求和非功能需求，为后续系统设计、编码实现、测试验收、部署维护和成果展示提供依据。'
    Add-Heading $doc '1.2 背景' 2
    Add-Paragraph $doc '随着家庭影像资料数字化需求不断增加，纸质老照片在长期保存过程中常出现褪色、划痕、噪点、模糊、人脸细节缺失等问题。传统修复依赖专业图像处理工具和人工经验，普通用户学习成本较高，难以形成稳定、可复用的处理流程。'
    Add-Paragraph $doc '近年来，深度学习图像增强技术在超分辨率、人脸修复、噪声去除和图像语义理解方面逐渐成熟。本项目将 Real-ESRGAN、GFPGAN、SmolVLM 图像描述模型等能力与 Web 管理系统结合，使用户能够通过浏览器完成照片上传、智能修复、结果对比、历史追踪和个人相册归档。'
    Add-Heading $doc '1.3 系统现状与发展' 2
    Add-Paragraph $doc '目前常见的照片修复方式包括人工修图、移动端滤镜类工具和桌面端图像增强软件。人工修图效果可控但成本较高，滤镜类工具操作简单但处理深度有限，专业软件功能强大但对用户技能要求较高。面向普通用户的在线老照片修复系统，应在操作便捷性、修复效果、记录管理和数据隔离之间取得平衡。'
    Add-Paragraph $doc 'PhotoProject 采用前后端分离结构，前端提供 Vue 3 单页应用，后端提供 Spring Boot 业务接口，AI 模型服务通过独立 HTTP 接口接入。该结构便于后续扩展批量修复、黑白照片上色、云端模型调度、相册标签体系和多端访问能力。'

    Add-Heading $doc '2 需求概述' 1
    Add-Heading $doc '2.1 目标' 2
    Add-Paragraph $doc '本系统的总体目标是建设一个流程完整、界面友好、易于部署的老照片智能修复平台。用户注册登录后，可以上传本地照片，选择修复模式，查看处理进度，对比原图与修复图，下载修复结果，或将结果保存到个人相册指定分组。'
    Add-Paragraph $doc '系统应支持用户认证、照片修复、历史记录、个人相册、个人中心、密码找回、AI 智能分类等核心功能，并通过数据库、缓存、文件存储和模型服务协同完成业务闭环。'
    Add-Heading $doc '2.2 用户特点' 2
    Add-Paragraph $doc '系统主要面向具有老照片数字化保存需求的个人用户。该类用户通常希望操作流程简单、修复结果直观、历史记录可追溯，并能将处理后的照片按家庭相册习惯进行分组管理。系统管理员或开发维护人员则关注服务部署、模型接口配置、数据安全和异常排查。'
    Add-Heading $doc '2.3 研究内容' 2
    Add-Paragraph $doc '本项目的主要研究内容包括：前端使用 Vue 3、Vite、Element Plus、Pinia 和 Axios 构建登录注册、首页、照片修复、历史记录、相册、个人中心等页面；后端使用 Spring Boot、Spring Data JPA、Spring Security、JWT、MySQL 和 Redis 构建业务服务；AI 服务集成 Real-ESRGAN、GFPGAN 和 SmolVLM，实现超分增强、人脸修复和相册智能分类。'

    Add-Heading $doc '3 功能需求' 1
    Add-Heading $doc '3.1 课题内容' 2
    Add-Paragraph $doc '本课题主要实现“老照片修复系统”的设计与开发。系统围绕用户账号、图片上传、智能修复、结果展示、历史记录、相册管理、智能分类、资料维护和密码找回形成完整业务流程。'
    Add-Figure $doc '图3.1 老照片修复系统用例图.png' '图3.1 老照片修复系统用例图'

    Add-Heading $doc '3.2 系统需求分析' 2
    Add-Heading $doc '3.2.1 系统角色' 3
    Add-Paragraph $doc '本系统主要包括普通用户和系统服务两类角色。普通用户可以注册、登录、上传照片、查看修复结果、管理历史记录和个人相册。系统服务包括 Web 前端、后端业务服务、数据库服务、Redis 缓存服务、文件服务以及 AI 模型服务，负责支撑用户操作和后台处理。'
    Add-Figure $doc '图3.2 用户用例说明图.png' '图3.2 用户用例说明图'
    Add-Heading $doc '3.2.2 系统用例分析' 3
    Add-Paragraph $doc '普通用户登录后可以使用以下功能：注册账号、登录系统、找回密码；上传老照片并选择自动综合修复、超分增强或人脸清晰化等模式；查看任务进度、修复状态、损伤类型、质量评分和处理耗时；通过滑动对比或并排对比查看修复效果；下载结果或保存到个人相册；查看、搜索、筛选和删除历史记录；创建相册分组、移动图片、删除图片、批量管理图片并执行一键智能分类。'
    Add-Paragraph $doc '系统服务需要完成以下职责：验证用户 Token 并保护受限接口；保存上传图片与修复后图片；创建修复记录和任务记录；调用 Real-ESRGAN、GFPGAN、SmolVLM 等模型服务；在任务完成或失败后更新数据库状态；为首页和历史页面提供统计与查询接口；为忘记密码流程发送和校验验证码；所有业务接口统一返回响应结构，便于前端处理成功、失败和异常状态。'

    Add-Heading $doc '3.3 功能模块图' 2
    Add-Paragraph $doc '针对系统使用流程，系统功能划分为用户认证、照片修复、历史记录、个人相册、个人中心、AI 模型服务六个主要模块。各模块通过后端统一接口连接数据库、文件系统和模型服务。'
    Add-Figure $doc '图3.3 系统功能模块图.png' '图3.3 系统功能模块图'

    Add-Heading $doc '3.4 系统模块需求说明' 2
    Add-Heading $doc '3.4.1 用户认证子系统功能' 3
    Add-Paragraph $doc '用户认证子系统应支持注册、登录、Token 校验、当前用户信息获取、密码找回验证码发送、验证码校验和密码重置。注册时系统应校验用户名、邮箱和密码信息；登录成功后返回用户信息和访问令牌；前端在访问首页、修复、历史记录、相册和个人中心等受保护页面时应携带 Token。'
    Add-Figure $doc '图3.4 用户认证系统功能图.png' '图3.4 用户认证系统功能图'

    Add-Heading $doc '3.4.2 照片修复子系统功能' 3
    Add-Paragraph $doc '照片修复子系统是系统核心模块。用户选择本地图片后，系统应校验文件格式和大小，将文件上传至后端，创建修复记录与任务记录，并根据修复模式调用对应 AI 服务。超分增强模式调用 Real-ESRGAN 服务，人脸修复模式调用 GFPGAN 服务，普通演示模式可完成基础复制和状态流转。'
    Add-Paragraph $doc '系统应记录原图路径、修复图路径、修复模式、任务状态、质量评分、损伤类型、处理耗时和错误信息。任务状态包括待处理、处理中、已完成、失败。处理完成后前端应支持原图与结果图的滑动对比、并排对比、下载结果和保存到相册。'
    Add-Figure $doc '图3.5 照片修复系统功能图.png' '图3.5 照片修复系统功能图'

    Add-Heading $doc '3.4.3 历史记录子系统功能' 3
    Add-Paragraph $doc '历史记录子系统用于展示用户全部修复记录。系统应支持分页查询，展示修复 ID、日期、损伤类型、修复模式、状态、质量评分、耗时、原图和修复图等信息，并支持按状态筛选、按关键词搜索、查看详情、下载修复结果和删除记录。'
    Add-Figure $doc '图3.6 历史记录系统功能图.png' '图3.6 历史记录系统功能图'

    Add-Heading $doc '3.4.4 个人相册子系统功能' 3
    Add-Paragraph $doc '个人相册子系统用于保存和整理用户图片。用户可以创建分组、删除分组、进入分组查看图片、预览图片、删除图片、移动图片，并可在分组内使用批量选择、批量删除和批量移动功能。系统默认提供“未分类”分组，保存到相册的图片应记录用户 ID、分组名、文件名、绝对路径、文件大小和上传时间。'
    Add-Paragraph $doc '相册一键分类功能应调用 SmolVLM 图像描述服务，根据图片内容生成标签，并将未分类照片移动到人物、风景、建筑、合影或智能分类等合适分组。AI 服务不可用时应提供兜底分组并保留错误日志。'
    Add-Figure $doc '图3.7 个人相册系统功能图.png' '图3.7 个人相册系统功能图'

    Add-Heading $doc '3.4.5 个人中心子系统功能' 3
    Add-Paragraph $doc '个人中心子系统用于用户资料维护。用户可以查看当前账号信息，修改用户名、邮箱、手机号、头像地址和个人简介，也可以通过输入旧密码和新密码完成密码修改。系统应保证用户只能修改自己的资料，并对输入内容进行必要校验。'
    Add-Figure $doc '图3.8 个人中心系统功能图.png' '图3.8 个人中心系统功能图'

    Add-Heading $doc '3.5 主要接口需求' 2
    Add-Table $doc @('模块', '接口', '方法', '说明') @(
        @('用户认证', '/api/auth/register', 'POST', '注册新用户'),
        @('用户认证', '/api/auth/login', 'POST', '用户登录并返回 Token'),
        @('用户认证', '/api/auth/forgot-password/send-code', 'POST', '发送找回密码验证码'),
        @('照片修复', '/api/restoration/upload', 'POST', '上传图片并创建修复任务'),
        @('照片修复', '/api/restoration/progress/{taskId}', 'GET', '查询修复任务进度'),
        @('照片修复', '/api/restoration/records', 'GET', '分页查询当前用户修复记录'),
        @('个人相册', '/api/album/groups', 'GET', '获取当前用户相册分组和图片'),
        @('个人相册', '/api/album/save', 'POST', '将图片保存到指定相册分组'),
        @('个人相册', '/api/album/classify', 'POST', '对未分类图片执行智能分类'),
        @('个人中心', '/api/users/{userId}', 'PUT', '修改用户个人资料')
    )

    Add-Heading $doc '3.6 数据需求' 2
    Add-Paragraph $doc '系统数据库采用 MySQL，主要数据实体包括用户信息、修复记录、修复任务和相册照片。用户表用于保存账号资料和登录状态信息；修复记录表用于保存每次图片修复的输入、输出、状态和评价信息；修复任务表用于记录异步任务执行状态；照片表用于保存用户相册中的图片和分组关系。'
    Add-Table $doc @('数据表', '主要字段', '用途') @(
        @('users', 'user_id、user_name、password、email、phone、avatar_url、bio、create_time', '保存用户账号和个人资料'),
        @('restoration_records', 'restoration_id、user_id、original_image_url、restored_image_url、restoration_mode、quality_score、status', '保存照片修复记录'),
        @('restoration_tasks', 'task_id、restoration_id、status、progress、retry_count、start_time、end_time', '保存异步修复任务状态'),
        @('photos', 'user_id、group_name、file_name、absolute_path、file_size、upload_time、is_classified', '保存个人相册图片归档信息')
    )

    Add-Heading $doc '4 非功能需求' 1
    Add-Heading $doc '4.1 性能需求' 2
    Add-Paragraph $doc '普通页面跳转、账号登录、列表查询和相册分组加载应在用户可接受时间内完成。图片修复任务由于依赖 AI 模型推理，系统应及时反馈上传结果、处理状态和进度信息，避免用户误以为系统无响应。系统应支持多用户同时访问，在合理硬件条件下保证核心接口稳定响应。'
    Add-Paragraph $doc '上传文件大小默认限制为 50MB，系统请求大小默认限制为 60MB。对于模型推理耗时较长的任务，后端应使用任务记录和状态查询机制支撑前端轮询展示。'
    Add-Heading $doc '4.2 运行环境需求' 2
    Add-Paragraph $doc '前台开发平台：Vue 3、Vite、Element Plus、Pinia、Axios。'
    Add-Paragraph $doc '后台开发平台：Java 11、Spring Boot 2.7、Spring Data JPA、Spring Security、Maven。'
    Add-Paragraph $doc '数据库与缓存：MySQL 8.0、Redis。'
    Add-Paragraph $doc 'AI 服务：Real-ESRGAN、GFPGAN、SmolVLM-Cap-0.6B 图像描述模型。'
    Add-Paragraph $doc '服务器环境：支持 Java、Python、Node.js、MySQL、Redis 的本地服务器或云服务器。模型服务器建议配置 NVIDIA GPU 与 CUDA 环境，以提升图像修复和分类速度；应用服务器建议内存 8GB 以上，并具备稳定磁盘空间用于保存图片文件。'
    Add-Heading $doc '4.3 安全性需求' 2
    Add-Paragraph $doc '系统应使用 Token 对受保护接口进行身份校验，未登录用户不能访问首页、照片修复、历史记录、个人相册和个人中心。用户只能查询和操作自己的修复记录、任务记录和相册数据。密码应加密存储，找回密码验证码应设置有效期，上传文件应限制类型和大小，文件访问接口应避免任意路径访问风险。'
    Add-Heading $doc '4.4 可维护性需求' 2
    Add-Paragraph $doc '系统代码应按照前后端分离结构组织。前端页面、路由、状态管理、接口请求和样式文件应保持清晰边界；后端控制器、服务、实体、仓储和配置层职责明确；AI 服务地址、文件上传目录、数据库连接、Redis 配置、邮件配置和模型参数应集中在配置文件中维护，便于部署迁移和功能扩展。'
    Add-Heading $doc '4.5 可靠性需求' 2
    Add-Paragraph $doc '系统应在 AI 服务不可用、上传失败、Token 失效、数据库异常、文件读写失败等情况下返回明确错误信息，并记录必要日志。修复任务应记录失败状态和错误原因，并具备有限重试能力。历史记录和相册数据应以用户 ID 作为隔离依据，避免跨用户数据混淆。'
    Add-Heading $doc '4.6 易用性需求' 2
    Add-Paragraph $doc '系统界面应围绕普通用户的照片修复流程组织，提供清晰的上传入口、模式选择、进度提示、结果对比、下载和保存相册操作。历史记录和相册页面应支持筛选、搜索、预览和批量操作，降低用户整理大量照片时的操作成本。'

    Add-Heading $doc '5 验收标准' 1
    Add-Paragraph $doc '系统完成后，应能够在本地或指定服务器环境中启动前端、后端、数据库、缓存和 AI 服务；用户可以完成注册登录、图片上传、修复处理、进度查询、结果对比、下载保存、历史记录管理、相册分组管理、智能分类和个人资料维护；核心接口在异常输入或服务不可用时能够给出明确提示；生成的修复记录、任务记录和相册图片能够正确持久化并按用户隔离。'

    $doc.SaveAs2($outputDoc, $wdFormatXMLDocument)
    $doc.Close($true)
}
finally {
    $word.Quit()
    [System.Runtime.InteropServices.Marshal]::ReleaseComObject($word) | Out-Null
}

Write-Output $outputDoc
