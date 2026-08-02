package dev.leeeet.wallpapermoe.service

internal data class ImageCrop(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/** The largest centered source rectangle with the destination's aspect ratio. */
internal fun centerCrop(
    sourceWidth: Int,
    sourceHeight: Int,
    destinationWidth: Int,
    destinationHeight: Int,
): ImageCrop {
    require(sourceWidth > 0 && sourceHeight > 0)
    require(destinationWidth > 0 && destinationHeight > 0)

    val sourceRatio = sourceWidth.toLong() * destinationHeight
    val destinationRatio = destinationWidth.toLong() * sourceHeight
    return when {
        sourceRatio > destinationRatio -> {
            val width = (sourceHeight.toLong() * destinationWidth / destinationHeight).toInt()
            val margin = (sourceWidth - width) / 2
            ImageCrop(margin, 0, sourceWidth - margin, sourceHeight)
        }
        sourceRatio < destinationRatio -> {
            val height = (sourceWidth.toLong() * destinationHeight / destinationWidth).toInt()
            val margin = (sourceHeight - height) / 2
            ImageCrop(0, margin, sourceWidth, sourceHeight - margin)
        }
        else -> ImageCrop(0, 0, sourceWidth, sourceHeight)
    }
}
