package cloud.carvis.backend.model.dtos

enum class ImageSize {
    ORIGINAL,
    `200`,
    `500`,
    `1080`;

    fun toInt(): Int =
        try {
            this.name.toInt()
        } catch (e: Error) {
            throw RuntimeException("ImageSize [$this] can not be converted to Integer", e)
        }
}
