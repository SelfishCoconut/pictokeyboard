package org.pictokeyboard.ime

import org.pictokeyboard.data.arasaac.ArasaacUrls
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoEntity
import java.io.File

/**
 * Coil models for the keyboard's own views, resolved once per database emission
 * rather than once per `onBindViewHolder`.
 *
 * Both functions stat the filesystem, which is exactly why they must not be
 * called from a bind path. `bind` runs on the main thread on every scroll frame
 * and every category switch, and on an IME the main thread is the typing path --
 * the one place a synchronous disk hit is least affordable. Resolving in the
 * flow instead moves the stat to [kotlinx.coroutines.Dispatchers.IO] and pays it
 * once for the whole list.
 *
 * The stat is what buys the CDN fallback, so it is worth keeping somewhere: a
 * cached file can vanish from under us -- the OS clearing app cache, a restore
 * onto a new device -- and the ARASAAC id is then the only route back to the
 * image. Handing the dead path straight to Coil would draw the placeholder and
 * leave a key permanently blank on a device that is perfectly able to fetch it.
 */

/** This picto's image: the local cache if it is really there, else ARASAAC. */
fun PictoEntity.keyboardImageModel(): Any? = cachedOrRemote(imagePath, arasaacId)

/** This category chip's icon, or null when the caregiver has not chosen one. */
fun CategoryEntity.keyboardIconModel(): Any? = cachedOrRemote(iconImagePath, iconArasaacId)

private fun cachedOrRemote(path: String?, arasaacId: Int?): Any? = when {
    path != null && File(path).exists() -> File(path)
    arasaacId != null -> ArasaacUrls.image(arasaacId)
    else -> null
}
