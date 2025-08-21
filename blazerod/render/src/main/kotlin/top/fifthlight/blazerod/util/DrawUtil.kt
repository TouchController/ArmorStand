package top.fifthlight.blazerod.util

import net.minecraft.client.render.VertexConsumer
import org.joml.Matrix4f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun VertexConsumer.drawBox(matrix: Matrix4f, halfSize: Float, color: Int) {
    vertex(matrix, -halfSize, -halfSize, halfSize).color(color)
    vertex(matrix, halfSize, -halfSize, halfSize).color(color)
    vertex(matrix, halfSize, halfSize, halfSize).color(color)
    vertex(matrix, -halfSize, halfSize, halfSize).color(color)

    vertex(matrix, -halfSize, -halfSize, -halfSize).color(color)
    vertex(matrix, -halfSize, halfSize, -halfSize).color(color)
    vertex(matrix, halfSize, halfSize, -halfSize).color(color)
    vertex(matrix, halfSize, -halfSize, -halfSize).color(color)

    vertex(matrix, -halfSize, -halfSize, -halfSize).color(color)
    vertex(matrix, -halfSize, -halfSize, halfSize).color(color)
    vertex(matrix, -halfSize, halfSize, halfSize).color(color)
    vertex(matrix, -halfSize, halfSize, -halfSize).color(color)

    vertex(matrix, halfSize, -halfSize, -halfSize).color(color)
    vertex(matrix, halfSize, halfSize, -halfSize).color(color)
    vertex(matrix, halfSize, halfSize, halfSize).color(color)
    vertex(matrix, halfSize, -halfSize, halfSize).color(color)

    vertex(matrix, -halfSize, halfSize, -halfSize).color(color)
    vertex(matrix, -halfSize, halfSize, halfSize).color(color)
    vertex(matrix, halfSize, halfSize, halfSize).color(color)
    vertex(matrix, halfSize, halfSize, -halfSize).color(color)

    vertex(matrix, -halfSize, -halfSize, -halfSize).color(color)
    vertex(matrix, halfSize, -halfSize, -halfSize).color(color)
    vertex(matrix, halfSize, -halfSize, halfSize).color(color)
    vertex(matrix, -halfSize, -halfSize, halfSize).color(color)
}

fun VertexConsumer.drawBoxWireframe(matrix: Matrix4f, width: Float, height: Float, length: Float, color: Int) {
    val halfWidth = width / 2.0f
    val halfHeight = height / 2.0f
    val halfLength = length / 2.0f

    vertex(matrix, -halfWidth, -halfHeight, halfLength).color(color).normal(0f, 1f, 0f)
    vertex(matrix, halfWidth, -halfHeight, halfLength).color(color).normal(0f, 1f, 0f)
    vertex(matrix, halfWidth, -halfHeight, halfLength).color(color).normal(0f, 1f, 0f)
    vertex(matrix, halfWidth, halfHeight, halfLength).color(color).normal(0f, 1f, 0f)
    vertex(matrix, halfWidth, halfHeight, halfLength).color(color).normal(0f, 1f, 0f)
    vertex(matrix, -halfWidth, halfHeight, halfLength).color(color).normal(0f, 1f, 0f)
    vertex(matrix, -halfWidth, halfHeight, halfLength).color(color).normal(0f, 1f, 0f)
    vertex(matrix, -halfWidth, -halfHeight, halfLength).color(color).normal(0f, 1f, 0f)

    vertex(matrix, -halfWidth, -halfHeight, -halfLength).color(color).normal(0f, 1f, 0f)
    vertex(matrix, halfWidth, -halfHeight, -halfLength).color(color).normal(0f, 1f, 0f)
    vertex(matrix, halfWidth, -halfHeight, -halfLength).color(color).normal(0f, 1f, 0f)
    vertex(matrix, halfWidth, halfHeight, -halfLength).color(color).normal(0f, 1f, 0f)
    vertex(matrix, halfWidth, halfHeight, -halfLength).color(color).normal(0f, 1f, 0f)
    vertex(matrix, -halfWidth, halfHeight, -halfLength).color(color).normal(0f, 1f, 0f)
    vertex(matrix, -halfWidth, halfHeight, -halfLength).color(color).normal(0f, 1f, 0f)
    vertex(matrix, -halfWidth, -halfHeight, -halfLength).color(color).normal(0f, 1f, 0f)

    vertex(matrix, -halfWidth, -halfHeight, halfLength).color(color).normal(0f, 1f, 0f)
    vertex(matrix, -halfWidth, -halfHeight, -halfLength).color(color).normal(0f, 1f, 0f)
    vertex(matrix, halfWidth, -halfHeight, halfLength).color(color).normal(0f, 1f, 0f)
    vertex(matrix, halfWidth, -halfHeight, -halfLength).color(color).normal(0f, 1f, 0f)
    vertex(matrix, halfWidth, halfHeight, halfLength).color(color).normal(0f, 1f, 0f)
    vertex(matrix, halfWidth, halfHeight, -halfLength).color(color).normal(0f, 1f, 0f)
    vertex(matrix, -halfWidth, halfHeight, halfLength).color(color).normal(0f, 1f, 0f)
    vertex(matrix, -halfWidth, halfHeight, -halfLength).color(color).normal(0f, 1f, 0f)
}

fun VertexConsumer.drawSphereWireframe(
    matrix: Matrix4f,
    radius: Float,
    segments: Int = 16,
    color: Int,
) {
    val angleStep = (2 * PI / segments).toFloat()

    for (i in 0..segments) {
        val latAngle = PI.toFloat() / 2 - i * angleStep
        val y = radius * sin(latAngle)
        val r = radius * cos(latAngle)

        var prevX = r * cos(0f)
        var prevZ = r * sin(0f)

        for (j in 1..segments) {
            val lonAngle = j * angleStep
            val x = r * cos(lonAngle)
            val z = r * sin(lonAngle)

            vertex(matrix, prevX, y, prevZ).color(color).normal(0f, 1f, 0f)
            vertex(matrix, x, y, z).color(color).normal(0f, 1f, 0f)

            prevX = x
            prevZ = z
        }
    }

    for (i in 0 until segments) {
        val lonAngle = i * angleStep

        var prevX = radius * cos(PI.toFloat() / 2) * cos(lonAngle)
        var prevY = radius * sin(PI.toFloat() / 2)
        var prevZ = radius * cos(PI.toFloat() / 2) * sin(lonAngle)

        for (j in 1..segments) {
            val latAngle = PI.toFloat() / 2 - j * angleStep
            val x = radius * cos(latAngle) * cos(lonAngle)
            val y = radius * sin(latAngle)
            val z = radius * cos(latAngle) * sin(lonAngle)

            vertex(matrix, prevX, prevY, prevZ).color(color).normal(0f, 1f, 0f)
            vertex(matrix, x, y, z).color(color).normal(0f, 1f, 0f)

            prevX = x
            prevY = y
            prevZ = z
        }
    }
}

fun VertexConsumer.drawCapsuleWireframe(
    matrix: Matrix4f,
    radius: Float,
    height: Float,
    segments: Int = 16,
    color: Int,
) {
    val halfHeight = height / 2.0f
    val angleStep = (2 * PI / segments).toFloat()

    for (i in 0..segments) {
        val angle = i * angleStep
        val nextAngle = (i + 1) * angleStep

        val x1 = radius * cos(angle)
        val z1 = radius * sin(angle)
        val x2 = radius * cos(nextAngle)
        val z2 = radius * sin(nextAngle)

        vertex(matrix, x1, halfHeight, z1).color(color).normal(0f, 1f, 0f)
        vertex(matrix, x2, halfHeight, z2).color(color).normal(0f, 1f, 0f)

        vertex(matrix, x1, -halfHeight, z1).color(color).normal(0f, 1f, 0f)
        vertex(matrix, x2, -halfHeight, z2).color(color).normal(0f, 1f, 0f)

        vertex(matrix, x1, halfHeight, z1).color(color).normal(0f, 1f, 0f)
        vertex(matrix, x1, -halfHeight, z1).color(color).normal(0f, 1f, 0f)
    }

    val topCenterY = halfHeight
    for (i in 0 until segments / 2) {
        val latAngleCurrent = PI.toFloat() / 2 - i * angleStep
        val latAngleNext = PI.toFloat() / 2 - (i + 1) * angleStep

        for (j in 0 until segments) {
            val lonAngleCurrent = j * angleStep
            val lonAngleNext = (j + 1) * angleStep

            val xCurrentLat = radius * cos(latAngleCurrent) * cos(lonAngleCurrent)
            val yCurrentLat = topCenterY + radius * sin(latAngleCurrent)
            val zCurrentLat = radius * cos(latAngleCurrent) * sin(lonAngleCurrent)

            val xNextLat = radius * cos(latAngleCurrent) * cos(lonAngleNext)
            val yNextLat = topCenterY + radius * sin(latAngleCurrent)
            val zNextLat = radius * cos(latAngleCurrent) * sin(lonAngleNext)

            vertex(matrix, xCurrentLat, yCurrentLat, zCurrentLat).color(color).normal(0f, 1f, 0f)
            vertex(matrix, xNextLat, yNextLat, zNextLat).color(color).normal(0f, 1f, 0f)

            val xCurrentLon = radius * cos(latAngleCurrent) * cos(lonAngleCurrent)
            val yCurrentLon = topCenterY + radius * sin(latAngleCurrent)
            val zCurrentLon = radius * cos(latAngleCurrent) * sin(lonAngleCurrent)

            val xNextLon = radius * cos(latAngleNext) * cos(lonAngleCurrent)
            val yNextLon = topCenterY + radius * sin(latAngleNext)
            val zNextLon = radius * cos(latAngleNext) * sin(lonAngleCurrent)

            vertex(matrix, xCurrentLon, yCurrentLon, zCurrentLon).color(color).normal(0f, 1f, 0f)
            vertex(matrix, xNextLon, yNextLon, zNextLon).color(color).normal(0f, 1f, 0f)
        }
    }
    val topPointY = topCenterY + radius
    for (j in 0 until segments) {
        val lonAngle = j * angleStep
        val firstLatAngle = PI.toFloat() / 2 - angleStep
        val xFirstLat = radius * cos(firstLatAngle) * cos(lonAngle)
        val yFirstLat = topCenterY + radius * sin(firstLatAngle)
        val zFirstLat = radius * cos(firstLatAngle) * sin(lonAngle)

        vertex(matrix, 0f, topPointY, 0f).color(color).normal(0f, 1f, 0f)
        vertex(matrix, xFirstLat, yFirstLat, zFirstLat).color(color).normal(0f, 1f, 0f)
    }

    val bottomCenterY = -halfHeight
    for (i in 0 until segments / 2) {
        val latAngleCurrent = -PI.toFloat() / 2 + i * angleStep
        val latAngleNext = -PI.toFloat() / 2 + (i + 1) * angleStep

        for (j in 0 until segments) {
            val lonAngleCurrent = j * angleStep
            val lonAngleNext = (j + 1) * angleStep

            val xCurrentLat = radius * cos(latAngleCurrent) * cos(lonAngleCurrent)
            val yCurrentLat = bottomCenterY + radius * sin(latAngleCurrent)
            val zCurrentLat = radius * cos(latAngleCurrent) * sin(lonAngleCurrent)

            val xNextLat = radius * cos(latAngleCurrent) * cos(lonAngleNext)
            val yNextLat = bottomCenterY + radius * sin(latAngleCurrent)
            val zNextLat = radius * cos(latAngleCurrent) * sin(lonAngleNext)

            vertex(matrix, xCurrentLat, yCurrentLat, zCurrentLat).color(color).normal(0f, 1f, 0f)
            vertex(matrix, xNextLat, yNextLat, zNextLat).color(color).normal(0f, 1f, 0f)

            val xCurrentLon = radius * cos(latAngleCurrent) * cos(lonAngleCurrent)
            val yCurrentLon = bottomCenterY + radius * sin(latAngleCurrent)
            val zCurrentLon = radius * cos(latAngleNext) * sin(lonAngleCurrent)

            val xNextLon = radius * cos(latAngleNext) * cos(lonAngleCurrent)
            val yNextLon = bottomCenterY + radius * sin(latAngleNext)
            val zNextLon = radius * cos(latAngleNext) * sin(lonAngleCurrent)

            vertex(matrix, xCurrentLon, yCurrentLon, zCurrentLon).color(color).normal(0f, 1f, 0f)
            vertex(matrix, xNextLon, yNextLon, zNextLon).color(color).normal(0f, 1f, 0f)
        }
    }

    val bottomPointY = bottomCenterY - radius
    for (j in 0 until segments) {
        val lonAngle = j * angleStep
        val firstLatAngle = -PI.toFloat() / 2 + angleStep
        val xFirstLat = radius * cos(firstLatAngle) * cos(lonAngle)
        val yFirstLat = bottomCenterY + radius * sin(firstLatAngle)
        val zFirstLat = radius * cos(firstLatAngle) * sin(lonAngle)

        vertex(matrix, 0f, bottomPointY, 0f).color(color).normal(0f, 1f, 0f)
        vertex(matrix, xFirstLat, yFirstLat, zFirstLat).color(color).normal(0f, 1f, 0f)
    }
}