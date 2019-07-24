package mandelbrot

import org.apache.commons.math3.complex.Complex

object Calculate {
  def calculateOne( c: Complex, iterations: Integer ) : Complex = {
    val zn = new Complex( 0, 0 )
    val range = 1 until iterations
    val ret = range.foldLeft( zn ) { (acc, item) =>
      acc.multiply( acc ).add( c )
    }

    ret
  }
}
