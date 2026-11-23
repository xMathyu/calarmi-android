package com.mathyusolutions.calarmi.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Los íconos se guardan con su nombre de SF Symbol (como en iOS, para que los
 * datos sean los mismos) y se pintan con su equivalente de Material.
 */
object SymbolIcons {
    private val map: Map<String, ImageVector> = mapOf(
        "alarm.fill" to Icons.Filled.Alarm,
        "bell.fill" to Icons.Filled.Notifications,
        "bell.badge.fill" to Icons.Filled.NotificationsActive,
        "birthday.cake.fill" to Icons.Filled.Cake,
        "book.fill" to Icons.AutoMirrored.Filled.MenuBook,
        "bookmark.fill" to Icons.Filled.Bookmark,
        "briefcase.fill" to Icons.Filled.Work,
        "calendar" to Icons.Filled.CalendarMonth,
        "calendar.badge.clock" to Icons.Filled.EventNote,
        "calendar.circle.fill" to Icons.Filled.Event,
        "calendar.badge.plus" to Icons.Filled.EditCalendar,
        "camera.fill" to Icons.Filled.PhotoCamera,
        "car.fill" to Icons.Filled.DirectionsCar,
        "cart.fill" to Icons.Filled.ShoppingCart,
        "checkmark.circle.fill" to Icons.Filled.CheckCircle,
        "clock.fill" to Icons.Filled.Schedule,
        "creditcard.fill" to Icons.Filled.CreditCard,
        "cross.case.fill" to Icons.Filled.MedicalServices,
        "cup.and.saucer.fill" to Icons.Filled.Coffee,
        "dumbbell.fill" to Icons.Filled.FitnessCenter,
        "figure.run" to Icons.AutoMirrored.Filled.DirectionsRun,
        "figure.walk" to Icons.AutoMirrored.Filled.DirectionsWalk,
        "figure.jumprope" to Icons.Filled.SportsGymnastics,
        "film.fill" to Icons.Filled.Movie,
        "flag.fill" to Icons.Filled.Flag,
        "fork.knife" to Icons.Filled.Restaurant,
        "gamecontroller.fill" to Icons.Filled.SportsEsports,
        "gift.fill" to Icons.Filled.CardGiftcard,
        "globe" to Icons.Filled.Public,
        "graduationcap.fill" to Icons.Filled.School,
        "heart.fill" to Icons.Filled.Favorite,
        "heart.circle.fill" to Icons.Filled.VolunteerActivism,
        "house.fill" to Icons.Filled.Home,
        "leaf.fill" to Icons.Filled.Eco,
        "lightbulb.fill" to Icons.Filled.Lightbulb,
        "list.bullet.clipboard.fill" to Icons.Filled.Assignment,
        "music.mic" to Icons.Filled.Mic,
        "music.note" to Icons.Filled.MusicNote,
        "music.quarternote.3" to Icons.Filled.QueueMusic,
        "paintbrush.fill" to Icons.Filled.Brush,
        "paintpalette.fill" to Icons.Filled.Palette,
        "party.popper.fill" to Icons.Filled.Celebration,
        "balloon.fill" to Icons.Filled.Celebration,
        "pawprint.fill" to Icons.Filled.Pets,
        "pencil" to Icons.Filled.Edit,
        "person.2.fill" to Icons.Filled.People,
        "pill.fill" to Icons.Filled.Medication,
        "pills.fill" to Icons.Filled.Medication,
        "rings.fill" to Icons.Filled.Diamond,
        "rosette" to Icons.Filled.MilitaryTech,
        "champagne.bottle" to Icons.Filled.LocalBar,
        "wineglass.fill" to Icons.Filled.WineBar,
        "sportscourt.fill" to Icons.Filled.SportsSoccer,
        "star.fill" to Icons.Filled.Star,
        "stethoscope" to Icons.Filled.MonitorHeart,
        "suitcase.rolling.fill" to Icons.Filled.Luggage,
        "airplane" to Icons.Filled.Flight,
        "tag.fill" to Icons.Filled.Sell,
        "ticket.fill" to Icons.Filled.ConfirmationNumber,
        "trophy.fill" to Icons.Filled.EmojiEvents,
        "video.fill" to Icons.Filled.Videocam,
        "hourglass" to Icons.Filled.HourglassBottom,
        "sparkles" to Icons.Filled.AutoAwesome,
        "waveform" to Icons.Filled.GraphicEq,
        "waveform.circle.fill" to Icons.Filled.Audiotrack,
        "speaker.wave.2.fill" to Icons.AutoMirrored.Filled.VolumeUp,
        "bell.and.waves.left.and.right.fill" to Icons.Filled.NotificationsActive,
        "dot.radiowaves.left.and.right" to Icons.Filled.Sensors,
        "plus.forwardslash.minus" to Icons.Filled.Calculate,
        "square.grid.3x3.fill" to Icons.Filled.GridOn,
        "number.square" to Icons.Filled.Pin,
        "rectangle.on.rectangle.angled" to Icons.Filled.Style,
        "eye.fill" to Icons.Filled.Visibility,
        "keyboard" to Icons.Filled.Keyboard,
        "wind" to Icons.Filled.Air,
        "target" to Icons.Filled.TrackChanges,
        "square.stack.3d.up.fill" to Icons.Filled.ViewInAr,
        "iphone.radiowaves.left.and.right" to Icons.Filled.Vibration,
        "circle.dotted.and.circle" to Icons.Filled.Adjust,
        "scope" to Icons.Filled.GpsFixed,
        "qrcode.viewfinder" to Icons.Filled.QrCodeScanner,
        "puzzlepiece.extension.fill" to Icons.Filled.Extension,
        "repeat" to Icons.Filled.Repeat,
        "gearshape" to Icons.Filled.Settings,
        "person.crop.circle.badge.plus" to Icons.Filled.PersonAdd,
        "person.badge.plus" to Icons.Filled.PersonAdd,
    )

    fun of(name: String?): ImageVector = name?.let(map::get) ?: Icons.Filled.Notifications

    fun has(name: String?): Boolean = name != null && map.containsKey(name)

    /** Los que ofrece el selector de íconos (además de los sugeridos por categoría). */
    val pickerSymbols: List<String> = listOf(
        "bell.fill", "alarm.fill", "pills.fill", "cross.case.fill", "stethoscope", "heart.fill",
        "birthday.cake.fill", "gift.fill", "party.popper.fill", "calendar", "clock.fill", "briefcase.fill",
        "graduationcap.fill", "book.fill", "house.fill", "cart.fill", "creditcard.fill", "car.fill",
        "airplane", "suitcase.rolling.fill", "fork.knife", "cup.and.saucer.fill", "dumbbell.fill", "figure.run",
        "sportscourt.fill", "gamecontroller.fill", "music.note", "music.mic", "film.fill", "camera.fill",
        "paintbrush.fill", "pawprint.fill", "leaf.fill", "person.2.fill", "ticket.fill", "trophy.fill",
        "star.fill", "flag.fill", "bookmark.fill", "tag.fill", "lightbulb.fill", "checkmark.circle.fill",
    )
}
