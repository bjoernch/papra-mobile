package app.papra.mobile.ui

import android.graphics.Bitmap
import android.graphics.PointF
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot

fun detectDocumentCorners(bitmap: Bitmap): List<PointF> {
    OpenCVLoader.initDebug()
    val src = Mat()
    Utils.bitmapToMat(bitmap, src)
    val gray = Mat()
    Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
    Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
    val edged = Mat()
    Imgproc.Canny(gray, edged, 75.0, 200.0)

    val contours = ArrayList<MatOfPoint>()
    Imgproc.findContours(edged, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
    val sorted = contours.sortedByDescending { Imgproc.contourArea(it) }.take(5)

    var docContour: MatOfPoint2f? = null
    for (contour in sorted) {
        val contour2f = MatOfPoint2f(*contour.toArray())
        val peri = Imgproc.arcLength(contour2f, true)
        val approx = MatOfPoint2f()
        Imgproc.approxPolyDP(contour2f, approx, 0.02 * peri, true)
        if (approx.total() == 4L) {
            docContour = approx
            break
        }
    }

    val corners = if (docContour == null) {
        listOf(
            PointF(0f, 0f),
            PointF(1f, 0f),
            PointF(1f, 1f),
            PointF(0f, 1f)
        )
    } else {
        val ordered = orderPoints(docContour.toArray())
        ordered.map { point ->
            PointF(
                (point.x / bitmap.width).toFloat().coerceIn(0f, 1f),
                (point.y / bitmap.height).toFloat().coerceIn(0f, 1f)
            )
        }
    }

    src.release()
    gray.release()
    edged.release()
    return corners
}

fun warpBitmapWithCorners(bitmap: Bitmap, corners: List<PointF>): Bitmap {
    OpenCVLoader.initDebug()
    val src = Mat()
    Utils.bitmapToMat(bitmap, src)
    val srcPoints = corners.map {
        Point(it.x * bitmap.width, it.y * bitmap.height)
    }
    val ordered = orderPoints(srcPoints.toTypedArray())
    val (tl, tr, br, bl) = ordered
    val widthA = distance(br, bl)
    val widthB = distance(tr, tl)
    val maxWidth = maxOf(widthA, widthB).toInt().coerceAtLeast(1)
    val heightA = distance(tr, br)
    val heightB = distance(tl, bl)
    val maxHeight = maxOf(heightA, heightB).toInt().coerceAtLeast(1)

    val dst = Mat(4, 1, CvType.CV_32FC2)
    dst.put(
        0, 0,
        0.0, 0.0,
        (maxWidth - 1).toDouble(), 0.0,
        (maxWidth - 1).toDouble(), (maxHeight - 1).toDouble(),
        0.0, (maxHeight - 1).toDouble()
    )
    val srcMat = Mat(4, 1, CvType.CV_32FC2)
    srcMat.put(
        0, 0,
        tl.x, tl.y,
        tr.x, tr.y,
        br.x, br.y,
        bl.x, bl.y
    )
    val transform = Imgproc.getPerspectiveTransform(srcMat, dst)
    val warped = Mat()
    Imgproc.warpPerspective(src, warped, transform, Size(maxWidth.toDouble(), maxHeight.toDouble()))

    val result = Bitmap.createBitmap(warped.cols(), warped.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(warped, result)

    src.release()
    srcMat.release()
    dst.release()
    transform.release()
    warped.release()
    return result
}

private fun orderPoints(points: Array<Point>): List<Point> {
    val sum = points.map { it.x + it.y }
    val diff = points.map { it.x - it.y }
    val tl = points[sum.indexOf(sum.minOrNull()!!)]
    val br = points[sum.indexOf(sum.maxOrNull()!!)]
    val tr = points[diff.indexOf(diff.minOrNull()!!)]
    val bl = points[diff.indexOf(diff.maxOrNull()!!)]
    return listOf(tl, tr, br, bl)
}

private fun distance(a: Point, b: Point): Double {
    return hypot(a.x - b.x, a.y - b.y)
}
