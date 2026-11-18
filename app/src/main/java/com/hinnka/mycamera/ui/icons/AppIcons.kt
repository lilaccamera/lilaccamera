package com.hinnka.mycamera.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

object AppIcons {
    val AutoMirroredArrowLeft: ImageVector
        get() {
            _autoMirroredArrowLeft?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.AutoMirroredArrowLeft",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = true,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M560-280 360-480l200-200v400Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _autoMirroredArrowLeft = it
            }
        }

    val AutoMirroredArrowRight: ImageVector
        get() {
            _autoMirroredArrowRight?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.AutoMirroredArrowRight",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = true,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M400-280v-400l200 200-200 200Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _autoMirroredArrowRight = it
            }
        }

    val AutoMirroredOutlinedUndo: ImageVector
        get() {
            _autoMirroredOutlinedUndo?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.AutoMirroredOutlinedUndo",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = true,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M280-200v-80h284q63 0 109.5-40T720-420q0-60-46.5-100T564-560H312l104 104-56 56-200-200 200-200 56 56-104 104h252q97 0 166.5 63T800-420q0 94-69.5 157T564-200H280Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _autoMirroredOutlinedUndo = it
            }
        }

    val AddPhotoAlternate: ImageVector
        get() {
            _addPhotoAlternate?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.AddPhotoAlternate",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M200-120q-33 0-56.5-23.5T120-200v-560q0-33 23.5-56.5T200-840h360q-20 26-30 57t-10 63q0 83 58.5 141.5T720-520q32 0 63-10t57-30v360q0 33-23.5 56.5T760-120H200Zm40-160h480L570-480 450-320l-90-120-120 160Zm440-320v-80h-80v-80h80v-80h80v80h80v80h-80v80h-80Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _addPhotoAlternate = it
            }
        }

    val Article: ImageVector
        get() {
            _article?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.Article",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
                autoMirror = true,
            ).apply {
                addPath(
                    pathData = addPathNodes("M19,3L5,3c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2L21,5c0,-1.1 -0.9,-2 -2,-2zM14,17L7,17v-2h7v2zM17,13L7,13v-2h10v2zM17,9L7,9L7,7h10v2z"),
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero,
                )
            }.build().also {
                _article = it
            }
        }

    val AutoAwesome: ImageVector
        get() {
            _autoAwesome?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.AutoAwesome",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("m760-600-50-110-110-50 110-50 50-110 50 110 110 50-110 50-50 110Zm0 560-50-110-110-50 110-50 50-110 50 110 110 50-110 50-50 110ZM360-160 260-380 40-480l220-100 100-220 100 220 220 100-220 100-100 220Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _autoAwesome = it
            }
        }

    val BarChart: ImageVector
        get() {
            _barChart?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.BarChart",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M640-160v-280h160v280H640Zm-240 0v-640h160v640H400Zm-240 0v-440h160v440H160Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _barChart = it
            }
        }

    val Bookmark: ImageVector
        get() {
            _bookmark?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.Bookmark",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
                autoMirror = false,
            ).apply {
                addPath(
                    pathData = addPathNodes("M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2z"),
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero,
                )
            }.build().also {
                _bookmark = it
            }
        }

    val BorderBottom: ImageVector
        get() {
            _borderBottom?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.BorderBottom",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M120-120v-80h720v80H120Zm0-160v-80h80v80h-80Zm0-160v-80h80v80h-80Zm0-160v-80h80v80h-80Zm0-160v-80h80v80h-80Zm160 320v-80h80v80h-80Zm0-320v-80h80v80h-80Zm160 480v-80h80v80h-80Zm0-160v-80h80v80h-80Zm0-160v-80h80v80h-80Zm0-160v-80h80v80h-80Zm160 320v-80h80v80h-80Zm0-320v-80h80v80h-80Zm160 480v-80h80v80h-80Zm0-160v-80h80v80h-80Zm0-160v-80h80v80h-80Zm0-160v-80h80v80h-80Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _borderBottom = it
            }
        }

    val BurstMode: ImageVector
        get() {
            _burstMode?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.BurstMode",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M40-200v-560h80v560H40Zm160 0v-560h80v560h-80Zm240 0q-33 0-56.5-23.5T360-280v-400q0-33 23.5-56.5T440-760h400q33 0 56.5 23.5T920-680v400q0 33-23.5 56.5T840-200H440Zm40-160h320L696-500l-76 100-56-74-84 114Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _burstMode = it
            }
        }

    val Business: ImageVector
        get() {
            _business?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.Business",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M80-120v-720h400v160h400v560H80Zm80-80h80v-80h-80v80Zm0-160h80v-80h-80v80Zm0-160h80v-80h-80v80Zm0-160h80v-80h-80v80Zm160 480h80v-80h-80v80Zm0-160h80v-80h-80v80Zm0-160h80v-80h-80v80Zm0-160h80v-80h-80v80Zm160 480h320v-400H480v80h80v80h-80v80h80v80h-80v80Zm160-240v-80h80v80h-80Zm0 160v-80h80v80h-80Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _business = it
            }
        }

    val CameraAlt: ImageVector
        get() {
            _cameraAlt?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.CameraAlt",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M480-260q75 0 127.5-52.5T660-440q0-75-52.5-127.5T480-620q-75 0-127.5 52.5T300-440q0 75 52.5 127.5T480-260Zm0-80q-42 0-71-29t-29-71q0-42 29-71t71-29q42 0 71 29t29 71q0 42-29 71t-71 29ZM160-120q-33 0-56.5-23.5T80-200v-480q0-33 23.5-56.5T160-760h126l74-80h240l74 80h126q33 0 56.5 23.5T880-680v480q0 33-23.5 56.5T800-120H160Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _cameraAlt = it
            }
        }

    val Cameraswitch: ImageVector
        get() {
            _cameraswitch?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.Cameraswitch",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M320-280q-33 0-56.5-23.5T240-360v-240q0-33 23.5-56.5T320-680h40l40-40h160l40 40h40q33 0 56.5 23.5T720-600v240q0 33-23.5 56.5T640-280H320Zm160-120q33 0 56.5-23.5T560-480q0-33-23.5-56.5T480-560q-33 0-56.5 23.5T400-480q0 33 23.5 56.5T480-400ZM342-940q34-11 68.5-15.5T480-960q94 0 177.5 33.5t148 93Q870-774 911-693.5T960-520h-80q-7-72-38-134.5T762.5-765Q714-813 651-842.5T516-878l62 62-56 56-180-180ZM618-20Q584-9 549.5-4.5T480 0q-94 0-177.5-33.5t-148-93Q90-186 49-266.5T0-440h80q8 72 38.5 134.5t79 110.5Q246-147 309-117.5T444-82l-62-62 56-56L618-20Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _cameraswitch = it
            }
        }

    /** Two turning arrows drawn as a 2 dp line at the default 26 dp size, for the camera switch. */
    val TurningArrows: ImageVector
        get() {
            _turningArrows?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.TurningArrows",
                defaultWidth = 26f.dp,
                defaultHeight = 26f.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                addPath(
                    pathData = addPathNodes("M2.6 8.6A10 10 0 0 1 20.4 9.6M21.4 15.4A10 10 0 0 1 3.6 14.4M21.5 5L20.4 9.6L15.9 8.6M2.5 19L3.6 14.4L8.1 15.4"),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f * 24f / 26f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }.build().also {
                _turningArrows = it
            }
        }

    // Mode dial pictograms: 24 dp line drawings with a 2 dp round stroke.
    val MoonStars: ImageVector
        get() = _moonStars ?: lineIcon(
            "AppIcons.MoonStars",
            "M12 3c.132 0 .263 0 .393 0a7.5 7.5 0 0 0 7.92 12.446a9 9 0 1 1 -8.313 -12.454z" +
                "M17 4a2 2 0 0 0 2 2a2 2 0 0 0 -2 2a2 2 0 0 0 -2 -2a2 2 0 0 0 2 -2M19 11h2m-1 -1v2"
        ).also { _moonStars = it }

    // Night's Fast choice (the bolt is the flash), and the dim-light reading on the top edge.
    val RunLine: ImageVector by lazy {
        lineIcon("AppIcons.RunLine", "M12 4a1 1 0 1 0 2 0a1 1 0 1 0 -2 0M4 17l5 1l.75 -1.5M15 21v-4l-4 -3l1 -6M7 12v-3l5 -1l3 3l3 1")
    }
    val SunLowLine: ImageVector by lazy {
        lineIcon(
            "AppIcons.SunLowLine",
            "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0M4 12h.01M12 4v.01M20 12h.01M12 20v.01M6.31 6.31l-.01 -.01" +
                "M17.71 6.31l-.01 .01M17.7 17.7l.01 .01M6.3 17.7l.01 -.01"
        )
    }

    val User: ImageVector
        get() = _user ?: lineIcon(
            "AppIcons.User",
            "M8 7a4 4 0 1 0 8 0a4 4 0 0 0 -8 0M6 21v-2a4 4 0 0 1 4 -4h4a4 4 0 0 1 4 4v2"
        ).also { _user = it }

    // Portrait guide: an info mark, a lens aperture for the blur, scissors for the subject cut-out.
    val DownloadLine: ImageVector by lazy { lineIcon("AppIcons.DownloadLine", "M12 4v11M7 10l5 5l5 -5M5 19h14") }
    val InfoLine: ImageVector by lazy { lineIcon("AppIcons.InfoLine", "M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0M12 9h.01M11 12h1v4h1") }
    val ApertureLine: ImageVector by lazy {
        lineIcon(
            "AppIcons.ApertureLine",
            "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0M3.6 15h10.55M6.551 4.938l3.26 10.034M17.032 4.636l-8.535 6.201" +
                "M20.559 14.51l-8.535 -6.201M12.257 20.916l3.261 -10.034"
        )
    }
    val ScissorsLine: ImageVector by lazy {
        lineIcon(
            "AppIcons.ScissorsLine",
            "M3 7a3 3 0 1 0 6 0a3 3 0 1 0 -6 0M3 17a3 3 0 1 0 6 0a3 3 0 1 0 -6 0M8.6 8.6l10.4 10.4M8.6 15.4l10.4 -10.4"
        )
    }

    val MovieCamera: ImageVector
        get() = _movieCamera ?: lineIcon(
            "AppIcons.MovieCamera",
            "M15 10l4.553 -2.276a1 1 0 0 1 1.447 .894v6.764a1 1 0 0 1 -1.447 .894l-4.553 -2.276v-4z" +
                "M5 6h8a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-8a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2z"
        ).also { _movieCamera = it }

    val Film: ImageVector
        get() = _film ?: lineIcon(
            "AppIcons.Film",
            "M6.5 3h11a2.5 2.5 0 0 1 2.5 2.5v13a2.5 2.5 0 0 1 -2.5 2.5h-11a2.5 2.5 0 0 1 -2.5 -2.5v-13a2.5 2.5 0 0 1 2.5 -2.5z" +
                "M8 3v18M16 3v18M4 7h2M4 12h2M4 17h2M18 7h2M18 12h2M18 17h2"
        ).also { _film = it }

    // Three slider lines with hollow knobs; the lines stop at each knob, so the icon has no fill to show on a picture.
    val Sliders: ImageVector
        get() = _sliders ?: lineIcon(
            "AppIcons.Sliders",
            "M3 6h3.6M11.4 6h9.6M3 12h10.6M18.4 12h2.6M3 18h1.6M9.4 18h11.6" +
                "M11.4 6a2.4 2.4 0 1 0 -4.8 0a2.4 2.4 0 1 0 4.8 0" +
                "M18.4 12a2.4 2.4 0 1 0 -4.8 0a2.4 2.4 0 1 0 4.8 0" +
                "M9.4 18a2.4 2.4 0 1 0 -4.8 0a2.4 2.4 0 1 0 4.8 0"
        ).also { _sliders = it }

    // Footer line icons for recording: two bars, a play triangle and a camera.
    val PauseLine: ImageVector
        get() = _pauseLine ?: lineIcon("AppIcons.PauseLine", "M9 6v12M15 6v12").also { _pauseLine = it }

    val PlayLine: ImageVector
        get() = _playLine ?: lineIcon("AppIcons.PlayLine", "M8 5v14l11 -7z").also { _playLine = it }

    val CameraLine: ImageVector
        get() = _cameraLine ?: lineIcon(
            "AppIcons.CameraLine",
            "M5 7h1a2 2 0 0 0 2 -2a1 1 0 0 1 1 -1h6a1 1 0 0 1 1 1a2 2 0 0 0 2 2h1a2 2 0 0 1 2 2v9a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-9a2 2 0 0 1 2 -2z" +
                "M9 13a3 3 0 1 0 6 0a3 3 0 0 0 -6 0"
        ).also { _cameraLine = it }

    // Line icons at the shared 2 dp stroke for the camera screen's controls and quick settings.
    private const val Slash = "M3 3l18 18"
    private const val BoltPath = "M13 3v7h6l-8 11v-7h-6l8 -11z"
    private const val BulbPath = "M3 12h1M12 3v1M20 12h1M5.6 5.6l.7 .7M18.4 5.6l-.7 .7" +
        "M9 16a5 5 0 1 1 6 0a3.5 3.5 0 0 0 -1 3a2 2 0 0 1 -4 0a3.5 3.5 0 0 0 -1 -3M9.7 17h4.6"
    val SwapLine: ImageVector by lazy { lineIcon("AppIcons.SwapLine", "M21 17h-18M6 10l-3 -3l3 -3M3 7h18M18 20l3 -3l-3 -3") }
    val StopwatchLine: ImageVector by lazy {
        lineIcon("AppIcons.StopwatchLine", "M5 13a7 7 0 1 0 14 0a7 7 0 0 0 -14 0zM14.5 10.5l-2.5 2.5M17 8l1 -1M14 3h-4")
    }
    val BoltLine: ImageVector by lazy { lineIcon("AppIcons.BoltLine", BoltPath) }
    val BoltOffLine: ImageVector by lazy { lineIcon("AppIcons.BoltOffLine", BoltPath + Slash) }
    val BulbLine: ImageVector by lazy { lineIcon("AppIcons.BulbLine", BulbPath) }
    val BulbOffLine: ImageVector by lazy { lineIcon("AppIcons.BulbOffLine", BulbPath + Slash) }
    // A ring of dots around a ring and a centre dot; the dots are zero-length strokes with round caps.
    val LivePhotoLine: ImageVector by lazy {
        lineIcon(
            "AppIcons.LivePhotoLine",
            "M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0" +
                "M15.9 20.11v.01M19.04 17.61v.01M20.77 14v.01M20.77 10v.01M19.04 6.39v.01M15.9 3.89v.01" +
                "M12 3v.01M8.1 3.89v.01M4.96 6.39v.01M3.23 10v.01M3.23 14v.01M4.96 17.61v.01M8.1 20.11v.01M12 21v.01"
        )
    }
    val ChevronRightLine: ImageVector by lazy { lineIcon("AppIcons.ChevronRightLine", "M9 6l6 6l-6 6") }
    val ChevronUpLine: ImageVector by lazy { lineIcon("AppIcons.ChevronUpLine", "M6 15l6 -6l6 6") }
    val ToolboxLine: ImageVector by lazy {
        lineIcon(
            "AppIcons.ToolboxLine",
            "M3 9a1 1 0 0 1 1 -1h16a1 1 0 0 1 1 1v9a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2zM8 8v-2a2 2 0 0 1 2 -2h4a2 2 0 0 1 2 2v2M3 13h18M10 13v2h4v-2"
        )
    }
    val FoldersLine: ImageVector by lazy {
        lineIcon(
            "AppIcons.FoldersLine",
            "M9 3h3l2 2h5a2 2 0 0 1 2 2v7a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-9a2 2 0 0 1 2 -2z" +
                "M17 16v2a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-9a2 2 0 0 1 2 -2h2"
        )
    }
    val SettingsLine: ImageVector by lazy {
        lineIcon(
            "AppIcons.SettingsLine",
            "M10.325 4.317c.426 -1.756 2.924 -1.756 3.35 0a1.724 1.724 0 0 0 2.573 1.066c1.543 -.94 3.31 .826 2.37 2.37" +
                "a1.724 1.724 0 0 0 1.065 2.572c1.756 .426 1.756 2.924 0 3.35a1.724 1.724 0 0 0 -1.066 2.573" +
                "c.94 1.543 -.826 3.31 -2.37 2.37a1.724 1.724 0 0 0 -2.572 1.065c-.426 1.756 -2.924 1.756 -3.35 0" +
                "a1.724 1.724 0 0 0 -2.573 -1.066c-1.543 .94 -3.31 -.826 -2.37 -2.37a1.724 1.724 0 0 0 -1.065 -2.572" +
                "c-1.756 -.426 -1.756 -2.924 0 -3.35a1.724 1.724 0 0 0 1.066 -2.573c-.94 -1.543 .826 -3.31 2.37 -2.37" +
                "c1 .608 2.296 .07 2.572 -1.065zM9 12a3 3 0 1 0 6 0a3 3 0 0 0 -6 0"
        )
    }
    val PhotoLine: ImageVector by lazy {
        lineIcon(
            "AppIcons.PhotoLine",
            "M15 8h.01M3 6a3 3 0 0 1 3 -3h12a3 3 0 0 1 3 3v12a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3zM3 16l5 -5c.928 -.893 2.072 -.893 3 0l5 5" +
                "M14 14l1 -1c.928 -.893 2.072 -.893 3 0l3 3"
        )
    }
    val CloseLine: ImageVector by lazy { lineIcon("AppIcons.CloseLine", "M18 6l-12 12M6 6l12 12") }
    val PlusLine: ImageVector by lazy { lineIcon("AppIcons.PlusLine", "M12 5v14M5 12h14") }
    val CheckLine: ImageVector by lazy { lineIcon("AppIcons.CheckLine", "M5 12l5 5l10 -10") }
    val UndoLine: ImageVector by lazy { lineIcon("AppIcons.UndoLine", "M9 14l-4 -4l4 -4M5 10h11a4 4 0 1 1 0 8h-1") }
    val CircleOffLine: ImageVector by lazy {
        lineIcon(
            "AppIcons.CircleOffLine",
            "M20.042 16.045a9 9 0 0 0 -12.087 -12.087M5.637 5.635a9 9 0 1 0 12.725 12.73M3 3l18 18"
        )
    }
    // A picture inside a border: a frame is added to the photo.
    val FrameLine: ImageVector by lazy {
        lineIcon(
            "AppIcons.FrameLine",
            "M3 5a2 2 0 0 1 2 -2h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2zM7 7h10v8h-10z"
        )
    }

    // Photo editor: edge icons, sections and values.
    val RedoLine: ImageVector by lazy { lineIcon("AppIcons.RedoLine", "M15 14l4 -4l-4 -4M19 10h-11a4 4 0 1 0 0 8h1") }
    val MaximizeLine: ImageVector by lazy {
        lineIcon(
            "AppIcons.MaximizeLine",
            "M4 8v-2a2 2 0 0 1 2 -2h2M4 16v2a2 2 0 0 0 2 2h2M16 4h2a2 2 0 0 1 2 2v2M16 20h2a2 2 0 0 0 2 -2v-2"
        )
    }
    val MinimizeLine: ImageVector by lazy {
        lineIcon(
            "AppIcons.MinimizeLine",
            "M15 19v-2a2 2 0 0 1 2 -2h2M15 5v2a2 2 0 0 0 2 2h2M5 15h2a2 2 0 0 1 2 2v2M5 9h2a2 2 0 0 0 2 -2v-2"
        )
    }
    val ContrastLine: ImageVector by lazy {
        lineIcon("AppIcons.ContrastLine", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0M12 17a5 5 0 0 0 0 -10v10")
    }
    val LoaderLine: ImageVector by lazy { lineIcon("AppIcons.LoaderLine", "M12 3a9 9 0 1 0 9 9") }
    val ArrowLeftLine: ImageVector by lazy { lineIcon("AppIcons.ArrowLeftLine", "M5 12h14M5 12l6 6M5 12l6 -6") }
    val MovieLine: ImageVector by lazy {
        lineIcon(
            "AppIcons.MovieLine",
            "M4 6a2 2 0 0 1 2 -2h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2z" +
                "M8 4v16M16 4v16M4 8h4M4 16h4M4 12h16M16 8h4M16 16h4"
        )
    }
    val SunLine: ImageVector by lazy {
        lineIcon(
            "AppIcons.SunLine",
            "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0M3 12h1m8 -9v1m8 8h1m-9 8v1" +
                "m-6.4 -15.4l.7 .7m12.1 -.7l-.7 .7m0 11.4l.7 .7m-12.1 -.7l-.7 .7"
        )
    }
    val PaletteLine: ImageVector by lazy {
        lineIcon(
            "AppIcons.PaletteLine",
            "M12 21a9 9 0 0 1 0 -18c4.97 0 9 3.582 9 8c0 1.06 -.474 2.078 -1.318 2.828" +
                "c-.844 .75 -1.989 1.172 -3.182 1.172h-2.5a2 2 0 0 0 -1 3.75a1.3 1.3 0 0 1 -1 2.25" +
                "M7.5 10.5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0M11.5 7.5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0" +
                "M15.5 10.5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0"
        )
    }
    val SparklesLine: ImageVector by lazy {
        lineIcon(
            "AppIcons.SparklesLine",
            "M16 18a2 2 0 0 1 2 2a2 2 0 0 1 2 -2a2 2 0 0 1 -2 -2a2 2 0 0 1 -2 2z" +
                "m0 -12a2 2 0 0 1 2 2a2 2 0 0 1 2 -2a2 2 0 0 1 -2 -2a2 2 0 0 1 -2 2z" +
                "m-7 12a6 6 0 0 1 6 -6a6 6 0 0 1 -6 -6a6 6 0 0 1 -6 6a6 6 0 0 1 6 6z"
        )
    }
    val FocusLine: ImageVector by lazy {
        lineIcon(
            "AppIcons.FocusLine",
            "M11.5 12a.5 .5 0 1 0 1 0a.5 .5 0 1 0 -1 0M5 12a7 7 0 1 0 14 0a7 7 0 1 0 -14 0" +
                "M12 3v2M3 12h2M12 19v2M19 12h2"
        )
    }
    val VideoLine: ImageVector by lazy {
        lineIcon(
            "AppIcons.VideoLine",
            "M15 10l4.553 -2.276a1 1 0 0 1 1.447 .894v6.764a1 1 0 0 1 -1.447 .894l-4.553 -2.276v-4z" +
                "M3 8a2 2 0 0 1 2 -2h8a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-8a2 2 0 0 1 -2 -2z"
        )
    }
    val SearchLine: ImageVector by lazy { lineIcon("AppIcons.SearchLine", "M3 10a7 7 0 1 0 14 0a7 7 0 1 0 -14 0M21 21l-6 -6") }
    val CropLine: ImageVector by lazy { lineIcon("AppIcons.CropLine", "M8 5v10a1 1 0 0 0 1 1h10M5 8h10a1 1 0 0 1 1 1v10") }
    val FiltersLine: ImageVector by lazy {
        lineIcon(
            "AppIcons.FiltersLine",
            "M7 8a5 5 0 1 0 10 0a5 5 0 1 0 -10 0M8 11a5 5 0 1 0 3.998 1.997M12.002 19.003a5 5 0 1 0 3.998 -8.003"
        )
    }
    val AdjustmentsLine: ImageVector by lazy {
        lineIcon(
            "AppIcons.AdjustmentsLine",
            "M12 6a2 2 0 1 0 4 0a2 2 0 1 0 -4 0M4 6h8M16 6h4M6 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0M4 12h2M10 12h10" +
                "M15 18a2 2 0 1 0 4 0a2 2 0 1 0 -4 0M4 18h11M19 18h1"
        )
    }
    val ExposureLine: ImageVector by lazy { lineIcon("AppIcons.ExposureLine", "M3 5a2 2 0 0 1 2 -2h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2zM4.6 19.4l14.8 -14.8M7 9h4m-2 -2v4M13 16h4") }
    val TemperatureLine: ImageVector by lazy { lineIcon("AppIcons.TemperatureLine", "M10 13.5a4 4 0 1 0 4 0v-8.5a2 2 0 0 0 -4 0v8.5M10 9h4") }
    val DropletHalfLine: ImageVector by lazy { lineIcon("AppIcons.DropletHalfLine", "M6.8 11a6 6 0 1 0 10.396 0l-5.197 -8l-5.2 8zM5 14h14") }
    val DropletLine: ImageVector by lazy { lineIcon("AppIcons.DropletLine", "M7.502 19.423c2.602 2.105 6.395 2.105 8.996 0c2.602 -2.105 3.262 -5.708 1.566 -8.546l-4.89 -7.26c-.42 -.625 -1.287 -.803 -1.936 -.397a1.376 1.376 0 0 0 -.41 .397l-4.893 7.26c-1.695 2.838 -1.035 6.441 1.567 8.546z") }
    val SwatchLine: ImageVector by lazy { lineIcon("AppIcons.SwatchLine", "M19 3h-4a2 2 0 0 0 -2 2v12a4 4 0 0 0 8 0v-12a2 2 0 0 0 -2 -2M13 7.35l-2 -2a2 2 0 0 0 -2.828 0l-2.828 2.828a2 2 0 0 0 0 2.828l9 9M7.3 13h-2.3a2 2 0 0 0 -2 2v4a2 2 0 0 0 2 2h12M17 17v.01") }
    val ChartLine: ImageVector by lazy { lineIcon("AppIcons.ChartLine", "M4 19h16M4 15l4 -6l4 2l4 -5l4 4") }
    val PhotoSensorLine: ImageVector by lazy { lineIcon("AppIcons.PhotoSensorLine", "M17 19h2a2 2 0 0 0 2 -2v-2M3 15v2a2 2 0 0 0 2 2h2M17 5h2a2 2 0 0 1 2 2v2M3 9v-2a2 2 0 0 1 2 -2h2M7 9a1 1 0 0 1 1 -1h8a1 1 0 0 1 1 1v6a1 1 0 0 1 -1 1h-8a1 1 0 0 1 -1 -1z") }
    val CircleHalfLine: ImageVector by lazy { lineIcon("AppIcons.CircleHalfLine", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0M12 3v18M12 14l7 -7M12 19l8.5 -8.5M12 9l4.5 -4.5") }
    val SlidersLine: ImageVector by lazy { lineIcon("AppIcons.SlidersLine", "M4 10a2 2 0 1 0 4 0a2 2 0 0 0 -4 0M6 4v4M6 12v8M10 16a2 2 0 1 0 4 0a2 2 0 0 0 -4 0M12 4v10M12 18v2M16 7a2 2 0 1 0 4 0a2 2 0 0 0 -4 0M18 4v1M18 9v11") }
    val GrainLine: ImageVector by lazy { lineIcon("AppIcons.GrainLine", "M3.5 9.5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0M8.5 4.5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0M8.5 14.5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0M3.5 19.5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0M13.5 9.5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0M18.5 4.5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0M13.5 19.5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0M18.5 14.5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0") }
    val SunHighLine: ImageVector by lazy { lineIcon("AppIcons.SunHighLine", "M14.828 14.828a4 4 0 1 0 -5.656 -5.656a4 4 0 0 0 5.656 5.656zM6.343 17.657l-1.414 1.414M6.343 6.343l-1.414 -1.414M17.657 6.343l1.414 -1.414M17.657 17.657l1.414 1.414M4 12h-2M12 4v-2M20 12h2M12 20v2") }
    val HalationLine: ImageVector by lazy { lineIcon("AppIcons.HalationLine", "M8 16a4 4 0 1 1 8 0M3 16h1M20 16h1M5.6 9.6l.7 .7M18.4 9.6l-.7 .7M12 7v1M3 20h18") }
    val BlurLine: ImageVector by lazy { lineIcon("AppIcons.BlurLine", "M12 21a9.01 9.01 0 0 0 2.32 -.302a9.004 9.004 0 0 0 1.74 -16.733a9 9 0 1 0 -4.06 17.035zM12 3v17M12 12h9M12 9h8M12 6h6M12 18h6M12 15h8") }
    val DropletOffLine: ImageVector by lazy { lineIcon("AppIcons.DropletOffLine", "M8.454 8.458l-1.554 2.42c-1.546 2.839 -1.146 6.442 1.456 8.546c2.602 2.105 6.395 2.105 8.996 0c1.215 -.983 1.97 -2.274 2.264 -3.65M18 14c-.12 -1.093 -.494 -2.134 -1.064 -3.124l-4.936 -7.876l-1.807 2.877M3 3l18 18") }
    val RainbowLine: ImageVector by lazy { lineIcon("AppIcons.RainbowLine", "M22 17c0 -5.523 -4.477 -10 -10 -10s-10 4.477 -10 10M18 17a6 6 0 1 0 -12 0M14 17a2 2 0 1 0 -4 0") }
    val TextureLine: ImageVector by lazy { lineIcon("AppIcons.TextureLine", "M6 3l-3 3M21 18l-3 3M11 3l-8 8M16 3l-13 13M21 3l-18 18M21 8l-13 13M21 13l-8 8") }
    val ClarityLine: ImageVector by lazy { lineIcon("AppIcons.ClarityLine", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0M12 7a5 5 0 0 0 0 10z") }
    val WandLine: ImageVector by lazy { lineIcon("AppIcons.WandLine", "M6 21l15 -15l-3 -3l-15 15l3 3M15 6l3 3M9 3a2 2 0 0 0 2 2a2 2 0 0 0 -2 2a2 2 0 0 0 -2 -2a2 2 0 0 0 2 -2M19 13a2 2 0 0 0 2 2a2 2 0 0 0 -2 2a2 2 0 0 0 -2 -2a2 2 0 0 0 2 -2") }
    val ColorFilterLine: ImageVector by lazy { lineIcon("AppIcons.ColorFilterLine", "M13.58 13.79c.27 .68 .42 1.43 .42 2.21c0 1.77 -.77 3.37 -2 4.46a5.94 5.94 0 0 1 -4 1.54c-3.31 0 -6 -2.69 -6 -6c0 -2.76 1.88 -5.1 4.42 -5.79M17.58 10.21c2.54 .69 4.42 3.03 4.42 5.79c0 3.31 -2.69 6 -6 6a5.94 5.94 0 0 1 -4 -1.54M6 8a6 6 0 1 0 12 0a6 6 0 1 0 -12 0") }
    val FocusCenteredLine: ImageVector by lazy { lineIcon("AppIcons.FocusCenteredLine", "M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0M4 8v-2a2 2 0 0 1 2 -2h2M4 16v2a2 2 0 0 0 2 2h2M16 4h2a2 2 0 0 1 2 2v2M16 20h2a2 2 0 0 0 2 -2v-2") }
    val CirclesLine: ImageVector by lazy { lineIcon("AppIcons.CirclesLine", "M3.5 7a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0M13.5 7a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0M8.5 17a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0") }
    val MoodSmileLine: ImageVector by lazy { lineIcon("AppIcons.MoodSmileLine", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0M9 10h.01M15 10h.01M9.5 15a3.5 3.5 0 0 0 5 0") }
    val CircleLine: ImageVector by lazy { lineIcon("AppIcons.CircleLine", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0") }
    val CircleDotLine: ImageVector by lazy { lineIcon("AppIcons.CircleDotLine", "M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0") }
    val RotateClockwiseLine: ImageVector by lazy { lineIcon("AppIcons.RotateClockwiseLine", "M4.05 11a8 8 0 1 1 .5 4m-.5 5v-5h5") }
    val BookmarkLine: ImageVector by lazy {
        lineIcon("AppIcons.BookmarkLine", "M18 7v14l-6 -4l-6 4v-14a4 4 0 0 1 4 -4h4a4 4 0 0 1 4 4z")
    }
    // Two overlapping frames: several shots in one picture.
    val MultiExposureLine: ImageVector by lazy {
        lineIcon(
            "AppIcons.MultiExposureLine",
            "M8 10a2 2 0 0 1 2 -2h8a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-8a2 2 0 0 1 -2 -2z" +
                "M16 8v-2a2 2 0 0 0 -2 -2h-8a2 2 0 0 0 -2 2v8a2 2 0 0 0 2 2h2"
        )
    }

    // Scene and condition hints from the camera (Tabler line icons; the tripod is drawn to match).
    val HazeLine: ImageVector by lazy { lineIcon("AppIcons.HazeLine", "M3 12h1M12 3v1M20 12h1M5.6 5.6l.7 .7M18.4 5.6l-.7 .7M8 12a4 4 0 1 1 8 0M3 16h18M3 20h18") }
    val MoonLine: ImageVector by lazy { lineIcon("AppIcons.MoonLine", "M12 3c.132 0 .263 0 .393 0a7.5 7.5 0 0 0 7.92 12.446a9 9 0 1 1 -8.313 -12.454z") }
    val CandleLine: ImageVector by lazy { lineIcon("AppIcons.CandleLine", "M9 21h6v-9a1 1 0 0 0 -1 -1h-4a1 1 0 0 0 -1 1v9zM12 3l1.465 1.638a2 2 0 1 1 -3.015 .099l1.55 -1.737z") }
    val MountainLine: ImageVector by lazy { lineIcon("AppIcons.MountainLine", "M3 20h18l-6.921 -14.612a2.3 2.3 0 0 0 -4.158 0l-6.921 14.612zM7.5 11l2 2.5l2.5 -2.5l2 3l2.5 -2") }
    val UserLine: ImageVector by lazy { lineIcon("AppIcons.UserLine", "M8 7a4 4 0 1 0 8 0a4 4 0 0 0 -8 0M6 21v-2a4 4 0 0 1 4 -4h4a4 4 0 0 1 4 4v2") }
    val FileTextLine: ImageVector by lazy { lineIcon("AppIcons.FileTextLine", "M14 3v4a1 1 0 0 0 1 1h4M17 21h-10a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2h7l5 5v11a2 2 0 0 1 -2 2zM9 9l1 0M9 13l6 0M9 17l6 0") }
    val KitchenLine: ImageVector by lazy { lineIcon("AppIcons.KitchenLine", "M19 3v12h-5c-.023 -3.681 .184 -7.406 5 -12zm0 12v6h-1v-3m-10 -14v17m-3 -17v3a3 3 0 1 0 6 0v-3") }
    val LampLine: ImageVector by lazy { lineIcon("AppIcons.LampLine", "M9 20h6M12 20v-8M5 12h14l-4 -8h-6z") }
    val MacroLine: ImageVector by lazy { lineIcon("AppIcons.MacroLine", "M6 15a6 6 0 1 0 12 0M18 15a6 6 0 0 0 -6 6M12 21a6 6 0 0 0 -6 -6M12 21v-10M12 11a5 5 0 0 1 -5 -5v-3l3 2l2 -2l2 2l3 -2v3a5 5 0 0 1 -5 5z") }
    val TripodLine: ImageVector by lazy { lineIcon("AppIcons.TripodLine", "M8 4a1 1 0 0 1 1 -1h6a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-6a1 1 0 0 1 -1 -1zM12 8v4M12 12l-5 9M12 12l5 9M12 12v9") }
    val WalkLine: ImageVector by lazy { lineIcon("AppIcons.WalkLine", "M12 4a1 1 0 1 0 2 0a1 1 0 1 0 -2 0M7 21l3 -4M16 21l-2 -4l-3 -3l1 -6M6 12l2 -3l4 -1l3 3l3 1") }

    private fun lineIcon(name: String, pathData: String): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24f.dp,
        defaultHeight = 24f.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = addPathNodes(pathData),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

    val CheckBox: ImageVector
        get() {
            _checkBox?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.CheckBox",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("m424-312 282-282-56-56-226 226-114-114-56 56 170 170ZM200-120q-33 0-56.5-23.5T120-200v-560q0-33 23.5-56.5T200-840h560q33 0 56.5 23.5T840-760v560q0 33-23.5 56.5T760-120H200Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _checkBox = it
            }
        }

    val CheckBoxOutlineBlank: ImageVector
        get() {
            _checkBoxOutlineBlank?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.CheckBoxOutlineBlank",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M200-120q-33 0-56.5-23.5T120-200v-560q0-33 23.5-56.5T200-840h560q33 0 56.5 23.5T840-760v560q0 33-23.5 56.5T760-120H200Zm0-80h560v-560H200v560Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _checkBoxOutlineBlank = it
            }
        }

    val ChevronRight: ImageVector
        get() {
            _chevronRight?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.ChevronRight",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M504-480 320-664l56-56 240 240-240 240-56-56 184-184Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _chevronRight = it
            }
        }

    val ContentCopy: ImageVector
        get() {
            _contentCopy?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.ContentCopy",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M360-240q-33 0-56.5-23.5T280-320v-480q0-33 23.5-56.5T360-880h360q33 0 56.5 23.5T800-800v480q0 33-23.5 56.5T720-240H360ZM200-80q-33 0-56.5-23.5T120-160v-560h80v560h440v80H200Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _contentCopy = it
            }
        }

    val Contrast: ImageVector
        get() {
            _contrast?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.Contrast",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M324-111.5Q251-143 197-197t-85.5-127Q80-397 80-480t31.5-156Q143-709 197-763t127-85.5Q397-880 480-880t156 31.5Q709-817 763-763t85.5 127Q880-563 880-480t-31.5 156Q817-251 763-197t-127 85.5Q563-80 480-80t-156-31.5ZM520-163q119-15 199.5-104.5T800-480q0-123-80.5-212.5T520-797v634Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _contrast = it
            }
        }

    val Crop169: ImageVector
        get() {
            _crop169?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.Crop169",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M200-280q-33 0-56.5-23.5T120-360v-240q0-33 23.5-56.5T200-680h560q33 0 56.5 23.5T840-600v240q0 33-23.5 56.5T760-280H200Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _crop169 = it
            }
        }

    val DirectionsWalk: ImageVector
        get() {
            _directionsWalk?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.DirectionsWalk",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("m280-40 112-564-72 28v136h-80v-188l202-86q14-6 29.5-7t29.5 4q14 5 26.5 14t20.5 23l40 64q26 42 70.5 69T760-520v80q-70 0-125-29t-94-74l-25 123 84 80v300h-80v-260l-84-64-72 324h-84Zm203.5-723.5Q460-787 460-820t23.5-56.5Q507-900 540-900t56.5 23.5Q620-853 620-820t-23.5 56.5Q573-740 540-740t-56.5-23.5Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _directionsWalk = it
            }
        }

    val Download: ImageVector
        get() {
            _download?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.Download",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M480-320 280-520l56-58 104 104v-326h80v326l104-104 56 58-200 200ZM240-160q-33 0-56.5-23.5T160-240v-120h80v120h480v-120h80v120q0 33-23.5 56.5T720-160H240Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _download = it
            }
        }

    val DragHandle: ImageVector
        get() {
            _dragHandle?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.DragHandle",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M160-360v-80h640v80H160Zm0-160v-80h640v80H160Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _dragHandle = it
            }
        }

    val ExpandLess: ImageVector
        get() {
            _expandLess?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.ExpandLess",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("m296-345-56-56 240-240 240 240-56 56-184-184-184 184Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _expandLess = it
            }
        }

    val ExpandMore: ImageVector
        get() {
            _expandMore?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.ExpandMore",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M480-345 240-585l56-56 184 184 184-184 56 56-240 240Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _expandMore = it
            }
        }

    val FilterFrames: ImageVector
        get() {
            _filterFrames?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.FilterFrames",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M160-80q-33 0-56.5-23.5T80-160v-560q0-33 23.5-56.5T160-800h160l160-160 160 160h160q33 0 56.5 23.5T880-720v560q0 33-23.5 56.5T800-80H160Zm0-80h640v-560H160v560Zm80-80v-400h480v400H240Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _filterFrames = it
            }
        }

    val FilterNone: ImageVector
        get() {
            _filterNone?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.FilterNone",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M320-240q-33 0-56.5-23.5T240-320v-480q0-33 23.5-56.5T320-880h480q33 0 56.5 23.5T880-800v480q0 33-23.5 56.5T800-240H320ZM160-80q-33 0-56.5-23.5T80-160v-560h80v560h560v80H160Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _filterNone = it
            }
        }

    val FilterVintage: ImageVector
        get() {
            _filterVintage?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.FilterVintage",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M381-116q-44-36-55-92-53 17-107-2t-83-66q-30-48-22-106.5t52-97.5q-42-38-50.5-94T134-678q27-48 81.5-69.5T324-752q11-56 55-92t101-36q57 0 101 36t55 92q56-17 108.5 3t81.5 71q27 50 19.5 104.5T794-480q44 39 52.5 96.5T828-276q-29 51-81.5 68T638-208q-11 56-55 92T482-80q-57 0-101-36Zm99-204q66 0 113-47t47-113q0-66-47-113t-113-47q-66 0-113 47t-47 113q0 66 47 113t113 47Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _filterVintage = it
            }
        }

    val Flag: ImageVector
        get() {
            _flag?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.Flag",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M200-120v-680h360l16 80h224v400H520l-16-80H280v280h-80Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _flag = it
            }
        }

    val FlashOff: ImageVector
        get() {
            _flashOff?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.FlashOff",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M280-880h400l-80 280h160L643-431 280-794v-86ZM400-80v-320H280v-166L55-791l57-57 736 736-57 57-241-241L400-80Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _flashOff = it
            }
        }

    val FlashOn: ImageVector
        get() {
            _flashOn?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.FlashOn",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M400-80v-320H280v-480h400l-80 280h160L400-80Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _flashOn = it
            }
        }

    val FlashlightOff: ImageVector
        get() {
            _flashlightOff?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.FlashlightOff",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M320-80v-448L56-792l56-56 736 736-56 56-152-152v128H320Zm-80-754v-46h480v120H314l-74-74Zm400 400L394-680h326v40l-80 120v86Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _flashlightOff = it
            }
        }

    val FlashlightOn: ImageVector
        get() {
            _flashlightOn?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.FlashlightOn",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M240-760v-120h480v120H240Zm282.5 402.5Q540-375 540-400t-17.5-42.5Q505-460 480-460t-42.5 17.5Q420-425 420-400t17.5 42.5Q455-340 480-340t42.5-17.5ZM320-80v-440l-80-120v-40h480v40l-80 120v440H320Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _flashlightOn = it
            }
        }

    val Grain: ImageVector
        get() {
            _grain?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.Grain",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M240-160q-33 0-56.5-23.5T160-240q0-33 23.5-56.5T240-320q33 0 56.5 23.5T320-240q0 33-23.5 56.5T240-160Zm320 0q-33 0-56.5-23.5T480-240q0-33 23.5-56.5T560-320q33 0 56.5 23.5T640-240q0 33-23.5 56.5T560-160ZM400-320q-33 0-56.5-23.5T320-400q0-33 23.5-56.5T400-480q33 0 56.5 23.5T480-400q0 33-23.5 56.5T400-320Zm320 0q-33 0-56.5-23.5T640-400q0-33 23.5-56.5T720-480q33 0 56.5 23.5T800-400q0 33-23.5 56.5T720-320ZM240-480q-33 0-56.5-23.5T160-560q0-33 23.5-56.5T240-640q33 0 56.5 23.5T320-560q0 33-23.5 56.5T240-480Zm320 0q-33 0-56.5-23.5T480-560q0-33 23.5-56.5T560-640q33 0 56.5 23.5T640-560q0 33-23.5 56.5T560-480ZM400-640q-33 0-56.5-23.5T320-720q0-33 23.5-56.5T400-800q33 0 56.5 23.5T480-720q0 33-23.5 56.5T400-640Zm320 0q-33 0-56.5-23.5T640-720q0-33 23.5-56.5T720-800q33 0 56.5 23.5T800-720q0 33-23.5 56.5T720-640Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _grain = it
            }
        }

    val GridView: ImageVector
        get() {
            _gridView?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.GridView",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M120-520v-320h320v320H120Zm0 400v-320h320v320H120Zm400-400v-320h320v320H520Zm0 400v-320h320v320H520Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _gridView = it
            }
        }

    val Image: ImageVector
        get() {
            _image?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.Image",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M200-120q-33 0-56.5-23.5T120-200v-560q0-33 23.5-56.5T200-840h560q33 0 56.5 23.5T840-760v560q0 33-23.5 56.5T760-120H200Zm40-160h480L570-480 450-320l-90-120-120 160Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _image = it
            }
        }

    val Iso: ImageVector
        get() {
            _iso?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.Iso",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M200-120q-33 0-56.5-23.5T120-200v-560q0-33 23.5-56.5T200-840h560q33 0 56.5 23.5T840-760v560q0 33-23.5 56.5T760-120H200Zm0-80h560v-560L200-200Zm380-40v-80h-80v-60h80v-80h60v80h80v60h-80v80h-60ZM240-620h200v-60H240v60Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _iso = it
            }
        }

    val Label: ImageVector
        get() {
            _label?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.Label",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 20f,
                viewportHeight = 20f,
                autoMirror = true,
            ).apply {
                addPath(
                    pathData = addPathNodes("M13.63,4H4c-1.1,0 -2,0.9 -2,2v8c0,1.1 0.9,2 2,2h9.63c0.6,0 1.14,-0.29 1.46,-0.72L19,10l-3.91,-5.28c-0.32,-0.43 -0.85,-0.72 -1.46,-0.72z"),
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero,
                )
            }.build().also {
                _label = it
            }
        }

    val Layers: ImageVector
        get() {
            _layers?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.Layers",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M480-118 120-398l66-50 294 228 294-228 66 50-360 280Zm0-202L120-600l360-280 360 280-360 280Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _layers = it
            }
        }

    val MoreHoriz: ImageVector
        get() {
            _moreHoriz?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.MoreHoriz",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M240-400q-33 0-56.5-23.5T160-480q0-33 23.5-56.5T240-560q33 0 56.5 23.5T320-480q0 33-23.5 56.5T240-400Zm240 0q-33 0-56.5-23.5T400-480q0-33 23.5-56.5T480-560q33 0 56.5 23.5T560-480q0 33-23.5 56.5T480-400Zm240 0q-33 0-56.5-23.5T640-480q0-33 23.5-56.5T720-560q33 0 56.5 23.5T800-480q0 33-23.5 56.5T720-400Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _moreHoriz = it
            }
        }

    val OpenInFull: ImageVector
        get() {
            _openInFull?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.OpenInFull",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
                autoMirror = false,
            ).apply {
                addPath(
                    pathData = addPathNodes("M21,11l0,-8l-8,0l3.29,3.29l-10,10l-3.29,-3.29l0,8l8,0l-3.29,-3.29l10,-10z"),
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero,
                )
            }.build().also {
                _openInFull = it
            }
        }

    val Output: ImageVector
        get() {
            _output?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.Output",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 20f,
                viewportHeight = 20f,
                autoMirror = false,
            ).apply {
                addPath(
                    pathData = addPathNodes("M14,14l4,-4l-4,-4l-1.06,1.06l2.19,2.19l-7.13,0l0,1.5l7.13,0l-2.19,2.19z"),
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero,
                )
                addPath(
                    pathData = addPathNodes("M15.5,15.5h-11v-11h11V6H17V4.5C17,3.67 16.33,3 15.5,3h-11C3.67,3 3,3.67 3,4.5v11C3,16.33 3.67,17 4.5,17h11c0.83,0 1.5,-0.67 1.5,-1.5V14h-1.5V15.5z"),
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero,
                )
            }.build().also {
                _output = it
            }
        }

    val Palette: ImageVector
        get() {
            _palette?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.Palette",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M480-80q-82 0-155-31.5t-127.5-86Q143-252 111.5-325T80-480q0-83 32.5-156t88-127Q256-817 330-848.5T488-880q80 0 151 27.5t124.5 76q53.5 48.5 85 115T880-518q0 115-70 176.5T640-280h-74q-9 0-12.5 5t-3.5 11q0 12 15 34.5t15 51.5q0 50-27.5 74T480-80ZM303-457q17-17 17-43t-17-43q-17-17-43-17t-43 17q-17 17-17 43t17 43q17 17 43 17t43-17Zm120-160q17-17 17-43t-17-43q-17-17-43-17t-43 17q-17 17-17 43t17 43q17 17 43 17t43-17Zm200 0q17-17 17-43t-17-43q-17-17-43-17t-43 17q-17 17-17 43t17 43q17 17 43 17t43-17Zm120 160q17-17 17-43t-17-43q-17-17-43-17t-43 17q-17 17-17 43t17 43q17 17 43 17t43-17Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _palette = it
            }
        }

    val Pause: ImageVector
        get() {
            _pause?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.Pause",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M560-200v-560h160v560H560Zm-320 0v-560h160v560H240Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _pause = it
            }
        }

    // Pro guide: an image sensor for RAW, stacked frames for merging.
    val SensorLine: ImageVector by lazy {
        lineIcon(
            "AppIcons.SensorLine",
            "M5 6a1 1 0 0 1 1 -1h12a1 1 0 0 1 1 1v12a1 1 0 0 1 -1 1h-12a1 1 0 0 1 -1 -1zM9 9h6v6h-6z" +
                "M3 10h2M3 14h2M10 3v2M14 3v2M21 10h-2M21 14h-2M14 21v-2M10 21v-2"
        )
    }
    // Camera basics guide: the gestures, drawn as arrows round the control they act on.
    val ModeSwipeLine: ImageVector by lazy {
        lineIcon("AppIcons.ModeSwipeLine", "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0M2 12h3M19 12h3M4 10l-2 2l2 2M20 10l2 2l-2 2")
    }
    val SwipeDownLine: ImageVector by lazy { lineIcon("AppIcons.SwipeDownLine", "M5 3h14M12 7v13M7 15l5 5l5 -5") }
    val SwipeUpLine: ImageVector by lazy { lineIcon("AppIcons.SwipeUpLine", "M5 21h14M12 17v-13M7 9l5 -5l5 5") }
    val ZoomRingLine: ImageVector by lazy {
        lineIcon(
            "AppIcons.ZoomRingLine",
            "M8 16a4 4 0 1 0 8 0a4 4 0 1 0 -8 0M5 10a9 9 0 0 1 14 0M5 10l-.5 -3.5M5 10l3.4 -1M19 10l.5 -3.5M19 10l-3.4 -1"
        )
    }
    val StackLine: ImageVector by lazy { lineIcon("AppIcons.StackLine", "M12 4l-8 4l8 4l8 -4zM4 12l8 4l8 -4M4 16l8 4l8 -4") }

    val Photo: ImageVector
        get() {
            _photo?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.Photo",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M200-120q-33 0-56.5-23.5T120-200v-560q0-33 23.5-56.5T200-840h560q33 0 56.5 23.5T840-760v560q0 33-23.5 56.5T760-120H200Zm40-160h480L570-480 450-320l-90-120-120 160Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _photo = it
            }
        }

    val PhotoLibrary: ImageVector
        get() {
            _photoLibrary?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.PhotoLibrary",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M360-400h400L622-580l-92 120-62-80-108 140Zm-40 160q-33 0-56.5-23.5T240-320v-480q0-33 23.5-56.5T320-880h480q33 0 56.5 23.5T880-800v480q0 33-23.5 56.5T800-240H320ZM160-80q-33 0-56.5-23.5T80-160v-560h80v560h560v80H160Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _photoLibrary = it
            }
        }

    val PictureInPictureAlt: ImageVector
        get() {
            _pictureInPictureAlt?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.PictureInPictureAlt",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M440-280h320v-240H440v240ZM160-160q-33 0-56.5-23.5T80-240v-480q0-33 23.5-56.5T160-800h640q33 0 56.5 23.5T880-720v480q0 33-23.5 56.5T800-160H160Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _pictureInPictureAlt = it
            }
        }

    val Public: ImageVector
        get() {
            _public?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.Public",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M324-111.5Q251-143 197-197t-85.5-127Q80-397 80-480t31.5-156Q143-709 197-763t127-85.5Q397-880 480-880t156 31.5Q709-817 763-763t85.5 127Q880-563 880-480t-31.5 156Q817-251 763-197t-127 85.5Q563-80 480-80t-156-31.5ZM440-162v-78q-33 0-56.5-23.5T360-320v-40L168-552q-3 18-5.5 36t-2.5 36q0 121 79.5 212T440-162Zm276-102q41-45 62.5-100.5T800-480q0-98-54.5-179T600-776v16q0 33-23.5 56.5T520-680h-80v80q0 17-11.5 28.5T400-560h-80v80h240q17 0 28.5 11.5T600-440v120h40q26 0 47 15.5t29 40.5Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _public = it
            }
        }

    val PushPin: ImageVector
        get() {
            _pushPin?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.PushPin",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("m640-480 80 80v80H520v240l-40 40-40-40v-240H240v-80l80-80v-280h-40v-80h400v80h-40v280Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _pushPin = it
            }
        }

    val RestartAlt: ImageVector
        get() {
            _restartAlt?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.RestartAlt",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M440-122q-121-15-200.5-105.5T160-440q0-66 26-126.5T260-672l57 57q-38 34-57.5 79T240-440q0 88 56 155.5T440-202v80Zm80 0v-80q87-16 143.5-83T720-440q0-100-70-170t-170-70h-3l44 44-56 56-140-140 140-140 56 56-44 44h3q134 0 227 93t93 227q0 121-79.5 211.5T520-122Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _restartAlt = it
            }
        }

    val ScreenRotation: ImageVector
        get() {
            _screenRotation?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.ScreenRotation",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M480 0q-99 0-186.5-37.5t-153-103Q75-206 37.5-293.5T0-480h80q0 71 24 136t66.5 117Q213-175 272-138.5T401-87L296-192l56-56L588-12q-26 6-53.5 9T480 0Zm400-480q0-71-24-136t-66.5-117Q747-785 688-821.5T559-873l105 105-56 56-236-236q26-6 53.5-9t54.5-3q99 0 186.5 37.5t153 103q65.5 65.5 103 153T960-480h-80ZM496.05-182 182-496q-10.52-11-16.26-25-5.74-14-5.74-29t5.74-29q5.74-14 16.26-25l173.62-174q11.03-11 25.08-16.5 14.05-5.5 29.11-5.5 15.05 0 29.1 5.5T464-778l313.97 313.97Q789-453 794.5-439q5.5 14 5.5 29t-5.74 29q-5.74 14-16.26 25L604.42-182q-11.04 11-25.09 16.5-14.04 5.5-29.09 5.5t-29.1-5.5q-14.05-5.5-25.09-16.5ZM373-556q13 0 21.5-9t8.5-21q0-13-8.5-21.5T373-616q-12 0-21 8.5t-9 21.5q0 12 9 21t21 9Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _screenRotation = it
            }
        }

    val SelectAll: ImageVector
        get() {
            _selectAll?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.SelectAll",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M280-280v-400h400v400H280Zm80-80h240v-240H360v240ZM200-200v80q-33 0-56.5-23.5T120-200h80Zm-80-80v-80h80v80h-80Zm0-160v-80h80v80h-80Zm0-160v-80h80v80h-80Zm80-160h-80q0-33 23.5-56.5T200-840v80Zm80 640v-80h80v80h-80Zm0-640v-80h80v80h-80Zm160 640v-80h80v80h-80Zm0-640v-80h80v80h-80Zm160 640v-80h80v80h-80Zm0-640v-80h80v80h-80Zm160 640v-80h80q0 33-23.5 56.5T760-120Zm0-160v-80h80v80h-80Zm0-160v-80h80v80h-80Zm0-160v-80h80v80h-80Zm0-160v-80q33 0 56.5 23.5T840-760h-80Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _selectAll = it
            }
        }

    val Shuffle: ImageVector
        get() {
            _shuffle?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.Shuffle",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M560-160v-80h104L537-367l57-57 126 126v-102h80v240H560Zm-344 0-56-56 504-504H560v-80h240v240h-80v-104L216-160Zm151-377L160-744l56-56 207 207-56 56Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _shuffle = it
            }
        }

    val StarBorder: ImageVector
        get() {
            _starBorder?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.StarBorder",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("m354-287 126-76 126 77-33-144 111-96-146-13-58-136-58 135-146 13 111 97-33 143ZM233-120l65-281L80-590l288-25 112-265 112 265 288 25-218 189 65 281-247-149-247 149Zm247-350Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _starBorder = it
            }
        }

    val Stop: ImageVector
        get() {
            _stop?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.Stop",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M240-240v-480h480v480H240Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _stop = it
            }
        }

    val SwapHoriz: ImageVector
        get() {
            _swapHoriz?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.SwapHoriz",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M280-160 80-360l200-200 56 57-103 103h287v80H233l103 103-56 57Zm400-240-56-57 103-103H440v-80h287L624-743l56-57 200 200-200 200Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _swapHoriz = it
            }
        }

    val Timer: ImageVector
        get() {
            _timer?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.Timer",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M360-840v-80h240v80H360Zm80 440h80v-240h-80v240Zm-99.5 291.5Q275-137 226-186t-77.5-114.5Q120-366 120-440t28.5-139.5Q177-645 226-694t114.5-77.5Q406-800 480-800q62 0 119 20t107 58l56-56 56 56-56 56q38 50 58 107t20 119q0 74-28.5 139.5T734-186q-49 49-114.5 77.5T480-80q-74 0-139.5-28.5Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _timer = it
            }
        }

    /** Thermometer outline for the heat warning; drawn with [ThermometerReading] in a second colour. */
    val ThermometerOutline: ImageVector
        get() {
            _thermometerOutline?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.ThermometerOutline",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                addPath(
                    pathData = addPathNodes("M9.5 13.31V5.5a2.5 2.5 0 0 1 5 0v7.81a4.25 4.25 0 1 1-5 0Z"),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }.build().also {
                _thermometerOutline = it
            }
        }

    /** The reading line and bulb inside [ThermometerOutline], 1 dp clear of the outline all round. */
    val ThermometerReading: ImageVector
        get() {
            _thermometerReading?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.ThermometerReading",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                addPath(
                    pathData = addPathNodes("M12 5.5V16.75"),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1f,
                    strokeLineCap = StrokeCap.Round,
                )
                addPath(
                    pathData = addPathNodes("M9.75 16.75a2.25 2.25 0 1 0 4.5 0a2.25 2.25 0 1 0-4.5 0Z"),
                    fill = SolidColor(Color.Black),
                )
            }.build().also {
                _thermometerReading = it
            }
        }

    val Tune: ImageVector
        get() {
            _tune?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.Tune",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M440-120v-240h80v80h320v80H520v80h-80Zm-320-80v-80h240v80H120Zm160-160v-80H120v-80h160v-80h80v240h-80Zm160-80v-80h400v80H440Zm160-160v-240h80v80h160v80H680v80h-80Zm-480-80v-80h400v80H120Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _tune = it
            }
        }

    val Vibration: ImageVector
        get() {
            _vibration?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.Vibration",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M320-120q-33 0-56.5-23.5T240-200v-560q0-33 23.5-56.5T320-840h320q33 0 56.5 23.5T720-760v560q0 33-23.5 56.5T640-120H320Zm188.5-531.5Q520-663 520-680t-11.5-28.5Q497-720 480-720t-28.5 11.5Q440-697 440-680t11.5 28.5Q463-640 480-640t28.5-11.5ZM0-360v-240h80v240H0Zm120 80v-400h80v400h-80Zm760-80v-240h80v240h-80Zm-120 80v-400h80v400h-80Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _vibration = it
            }
        }

    val Stabilization: ImageVector
        get() {
            _stabilization?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.Stabilization",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
                autoMirror = false,
            ).apply {
                addPath(
                    pathData = addPathNodes(
                        "M11 20Q10.4 20 9.838 19.762Q9.275 19.525 8.85 19.1L4 14.1L5.325 12.95Q5.675 12.65 6.125 12.587Q6.575 12.525 7 12.75L8.5 13.55V7.2Q8.5 6.5 9.025 6Q9.55 5.5 10.25 5.5Q10.325 5.5 10.438 5.512Q10.55 5.525 10.625 5.55V5.5Q10.625 4.75 11.137 4.375Q11.65 4 12.25 4Q12.675 4 13.05 4.2Q13.425 4.4 13.65 4.725Q13.8 4.675 13.938 4.637Q14.075 4.6 14.25 4.6Q14.95 4.6 15.463 5.087Q15.975 5.575 15.975 6.275V6.45Q16.05 6.425 16.113 6.425Q16.175 6.425 16.25 6.425Q16.95 6.425 17.475 6.912Q18 7.4 18 8.1V17.5Q18 18.55 17.275 19.275Q16.55 20 15.5 20ZM11 18.5H15.5Q15.925 18.5 16.212 18.212Q16.5 17.925 16.5 17.5V8.175Q16.5 8.05 16.438 7.987Q16.375 7.925 16.25 7.925Q16.125 7.925 16.062 7.987Q16 8.05 16 8.175V12H14.5V6.35Q14.5 6.225 14.438 6.162Q14.375 6.1 14.25 6.1Q14.125 6.1 14.062 6.162Q14 6.225 14 6.35V12H12.5V5.75Q12.5 5.625 12.438 5.562Q12.375 5.5 12.25 5.5Q12.125 5.5 12.062 5.562Q12 5.625 12 5.75V12H10.5V7.25Q10.5 7.125 10.438 7.062Q10.375 7 10.25 7Q10.125 7 10.062 7.062Q10 7.125 10 7.25V16L6.3 14.075L6.175 14.2L9.9 18.05Q10.125 18.275 10.413 18.387Q10.7 18.5 11 18.5ZM3.2 20.6Q2.95 20.8 2.65 20.75Q2.35 20.7 2.15 20.45L0.15 17.775Q0 17.575 0 17.325Q0 17.075 0.15 16.875L1.8 14.65L0.15 12.45Q0 12.25 0 12Q0 11.75 0.15 11.55L1.8 9.325L0.15 7.125Q0 6.925 0 6.675Q0 6.425 0.15 6.225L2.15 3.55Q2.35 3.3 2.65 3.25Q2.95 3.2 3.2 3.4Q3.45 3.6 3.5 3.9Q3.55 4.2 3.35 4.45L1.7 6.675L3.35 8.9Q3.5 9.1 3.5 9.35Q3.5 9.6 3.35 9.8L1.7 12L3.35 14.225Q3.5 14.425 3.5 14.675Q3.5 14.925 3.35 15.125L1.7 17.35L3.35 19.55Q3.55 19.8 3.5 20.1Q3.45 20.4 3.2 20.6ZM19.8 20.625Q19.55 20.425 19.5 20.125Q19.45 19.825 19.65 19.575L21.3 17.35L19.65 15.125Q19.5 14.925 19.5 14.675Q19.5 14.425 19.65 14.225L21.3 12L19.65 9.775Q19.5 9.575 19.5 9.325Q19.5 9.075 19.65 8.875L21.3 6.65L19.65 4.45Q19.45 4.2 19.5 3.9Q19.55 3.6 19.8 3.4Q20.05 3.2 20.35 3.25Q20.65 3.3 20.85 3.55L22.85 6.225Q23 6.425 23 6.675Q23 6.925 22.85 7.125L21.2 9.35L22.85 11.55Q23 11.75 23 12Q23 12.25 22.85 12.45L21.2 14.675L22.85 16.9Q23 17.1 23 17.35Q23 17.6 22.85 17.8L20.85 20.475Q20.65 20.725 20.35 20.775Q20.05 20.825 19.8 20.625Z"
                    ),
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero,
                )
            }.build().also {
                _stabilization = it
            }
        }

    val Videocam: ImageVector
        get() {
            _videocam?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.Videocam",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M160-160q-33 0-56.5-23.5T80-240v-480q0-33 23.5-56.5T160-800h480q33 0 56.5 23.5T720-720v180l160-160v440L720-420v180q0 33-23.5 56.5T640-160H160Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _videocam = it
            }
        }

    val Visibility: ImageVector
        get() {
            _visibility?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.Visibility",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M607.5-372.5Q660-425 660-500t-52.5-127.5Q555-680 480-680t-127.5 52.5Q300-575 300-500t52.5 127.5Q405-320 480-320t127.5-52.5Zm-204-51Q372-455 372-500t31.5-76.5Q435-608 480-608t76.5 31.5Q588-545 588-500t-31.5 76.5Q525-392 480-392t-76.5-31.5ZM214-281.5Q94-363 40-500q54-137 174-218.5T480-800q146 0 266 81.5T920-500q-54 137-174 218.5T480-200q-146 0-266-81.5Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _visibility = it
            }
        }

    val VisibilityOff: ImageVector
        get() {
            _visibilityOff?.let { return it }
            return ImageVector.Builder(
                name = "AppIcons.VisibilityOff",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
                autoMirror = false,
            ).apply {
                group(
                    translationY = 960f,
                ) {
                    addPath(
                        pathData = addPathNodes("M792-56 624-222q-35 11-70.5 16.5T480-200q-151 0-269-83.5T40-500q21-53 53-98.5t73-81.5L56-792l56-56 736 736-56 56ZM480-320q11 0 20.5-1t20.5-4L305-541q-3 11-4 20.5t-1 20.5q0 75 52.5 127.5T480-320Zm292 18L645-428q7-17 11-34.5t4-37.5q0-75-52.5-127.5T480-680q-20 0-37.5 4T408-664L306-766q41-17 84-25.5t90-8.5q151 0 269 83.5T920-500q-23 59-60.5 109.5T772-302ZM587-486 467-606q28-5 51.5 4.5T559-574q17 18 24.5 41.5T587-486Z"),
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build().also {
                _visibilityOff = it
            }
        }

}

private var _autoMirroredArrowLeft: ImageVector? = null
private var _autoMirroredArrowRight: ImageVector? = null
private var _autoMirroredOutlinedUndo: ImageVector? = null
private var _addPhotoAlternate: ImageVector? = null
private var _article: ImageVector? = null
private var _autoAwesome: ImageVector? = null
private var _barChart: ImageVector? = null
private var _bookmark: ImageVector? = null
private var _borderBottom: ImageVector? = null
private var _burstMode: ImageVector? = null
private var _business: ImageVector? = null
private var _cameraAlt: ImageVector? = null
private var _cameraswitch: ImageVector? = null
private var _turningArrows: ImageVector? = null
private var _moonStars: ImageVector? = null
private var _user: ImageVector? = null
private var _movieCamera: ImageVector? = null
private var _film: ImageVector? = null
private var _sliders: ImageVector? = null
private var _pauseLine: ImageVector? = null
private var _playLine: ImageVector? = null
private var _cameraLine: ImageVector? = null
private var _checkBox: ImageVector? = null
private var _checkBoxOutlineBlank: ImageVector? = null
private var _chevronRight: ImageVector? = null
private var _contentCopy: ImageVector? = null
private var _contrast: ImageVector? = null
private var _crop169: ImageVector? = null
private var _directionsWalk: ImageVector? = null
private var _download: ImageVector? = null
private var _dragHandle: ImageVector? = null
private var _expandLess: ImageVector? = null
private var _expandMore: ImageVector? = null
private var _filterFrames: ImageVector? = null
private var _filterNone: ImageVector? = null
private var _filterVintage: ImageVector? = null
private var _flag: ImageVector? = null
private var _flashOff: ImageVector? = null
private var _flashOn: ImageVector? = null
private var _flashlightOff: ImageVector? = null
private var _flashlightOn: ImageVector? = null
private var _grain: ImageVector? = null
private var _gridView: ImageVector? = null
private var _image: ImageVector? = null
private var _iso: ImageVector? = null
private var _label: ImageVector? = null
private var _layers: ImageVector? = null
private var _moreHoriz: ImageVector? = null
private var _openInFull: ImageVector? = null
private var _output: ImageVector? = null
private var _palette: ImageVector? = null
private var _pause: ImageVector? = null
private var _photo: ImageVector? = null
private var _photoLibrary: ImageVector? = null
private var _pictureInPictureAlt: ImageVector? = null
private var _public: ImageVector? = null
private var _pushPin: ImageVector? = null
private var _restartAlt: ImageVector? = null
private var _screenRotation: ImageVector? = null
private var _selectAll: ImageVector? = null
private var _shuffle: ImageVector? = null
private var _starBorder: ImageVector? = null
private var _stop: ImageVector? = null
private var _swapHoriz: ImageVector? = null
private var _timer: ImageVector? = null
private var _thermometerOutline: ImageVector? = null
private var _thermometerReading: ImageVector? = null
private var _tune: ImageVector? = null
private var _vibration: ImageVector? = null
private var _stabilization: ImageVector? = null
private var _videocam: ImageVector? = null
private var _visibility: ImageVector? = null
private var _visibilityOff: ImageVector? = null
