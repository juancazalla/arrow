package arrow.optics

import arrow.autofold

@autofold
sealed class WithoutGenerics {
  data class First(val int: Int) : WithoutGenerics()
  data class Second(val int: Int, val long: Long) : WithoutGenerics()
  data class Third(val int: Int, val long: Long, val string: String) : WithoutGenerics()
}

@autofold
sealed class WithGenerics<out A, out B, out C> {
  data class First<A>(val a: A) : WithGenerics<A, Nothing, Nothing>()
  data class Second<A, B>(val a: A, val b: B) : WithGenerics<A, B, Nothing>()
  data class Third<A, B, C>(val a: A, val b: B, val c: C) : WithGenerics<A, B, C>()
}

@autofold
sealed class FailGenerics<A> {
  data class Second<A, B>(val a: A, val b: B) : FailGenerics<A>()
}
