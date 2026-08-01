package org.pictokeyboard.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Taking a picture with the device's own camera app.
 *
 * The app deliberately holds no CAMERA permission: it hands a camera app a file
 * to write into and gets a result back, which needs no grant and no permission
 * prompt in front of a caregiver who is mid-task.
 */

/**
 * True when some app on the device can actually take a picture for us. Requires
 * the IMAGE_CAPTURE `<queries>` entry in the manifest — without it package
 * visibility hides every camera app and this always answers false.
 */
internal fun Context.hasCameraApp(): Boolean =
    packageManager.resolveActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE), PackageManager.MATCH_DEFAULT_ONLY) != null

/**
 * A file we own for the camera to write into, exposed through the app's
 * FileProvider. It lands in the cache because it is scratch: the picture is
 * cropped and copied into the picto cache before it is used for anything.
 */
internal fun Context.newCameraUri(): Uri {
    val dir = File(cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "shot-${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
}
