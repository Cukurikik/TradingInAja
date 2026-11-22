package com.tradinginaja.domain.engine

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Utilitas dekomposisi Cholesky untuk mengubah vector shock independen Z ~ N(0, I)
 * menjadi shock terkorelasi Z_corr = L * Z dengan matriks kovarians/korelasi R = L * L^T.
 */
object CholeskyDecomposition {

    private const val SYMMETRY_TOLERANCE = 1e-8
    private const val DIAGONAL_TOLERANCE = 1e-8
    private const val MIN_PIVOT_VALUE = 1e-10

    fun identityMatrix(size: Int): Array<DoubleArray> {
        require(size > 0) { "Ukuran matriks harus lebih besar dari nol." }
        return Array(size) { row ->
            DoubleArray(size) { col -> if (row == col) 1.0 else 0.0 }
        }
    }

    /**
     * Menghitung matriks segitiga bawah L sehingga L * L^T = matrix.
     * Melempar [IllegalArgumentException] jika matriks tidak simetris atau tidak positive-definite.
     */
    fun decompose(matrix: Array<DoubleArray>): Array<DoubleArray> {
        val n = matrix.size
        require(n > 0) { "Matriks korelasi tidak boleh kosong." }
        validateSquareAndSymmetric(matrix, n)

        val lower = Array(n) { DoubleArray(n) }
        for (i in 0 until n) {
            for (j in 0..i) {
                var sum = 0.0
                for (k in 0 until j) {
                    sum += lower[i][k] * lower[j][k]
                }
                if (i == j) {
                    val diag = matrix[i][i] - sum
                    require(diag > MIN_PIVOT_VALUE) {
                        "Matriks korelasi tidak positive-definite pada baris $i (diag=$diag)."
                    }
                    lower[i][j] = sqrt(diag)
                } else {
                    lower[i][j] = (matrix[i][j] - sum) / lower[j][j]
                }
            }
        }
        return lower
    }

    /**
     * Mengalikan matriks segitiga bawah L dengan vektor shock independen [independentNormals].
     */
    fun correlate(lowerTriangular: Array<DoubleArray>, independentNormals: DoubleArray): DoubleArray {
        val n = lowerTriangular.size
        require(independentNormals.size == n) {
            "Panjang vektor shock (${independentNormals.size}) harus sama dengan ukuran matriks ($n)."
        }
        val correlated = DoubleArray(n)
        for (i in 0 until n) {
            var acc = 0.0
            val row = lowerTriangular[i]
            for (j in 0..i) {
                acc += row[j] * independentNormals[j]
            }
            correlated[i] = acc
        }
        return correlated
    }

    private fun validateSquareAndSymmetric(matrix: Array<DoubleArray>, n: Int) {
        for (i in 0 until n) {
            require(matrix[i].size == n) { "Matriks korelasi harus bujur sangkar ($n x $n)." }
            require(abs(matrix[i][i] - 1.0) <= DIAGONAL_TOLERANCE) {
                "Diagonal utama matriks korelasi pada [$i][$i] harus bernilai 1.0."
            }
            for (j in 0 until i) {
                require(abs(matrix[i][j] - matrix[j][i]) <= SYMMETRY_TOLERANCE) {
                    "Matriks korelasi tidak simetris pada [$i][$j]."
                }
                require(matrix[i][j] in -1.0..1.0) {
                    "Nilai korelasi pada [$i][$j] harus berada di [-1.0, 1.0]."
                }
            }
        }
    }
}
