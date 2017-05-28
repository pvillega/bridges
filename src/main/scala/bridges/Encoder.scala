package bridges

import shapeless.labelled.FieldType
import shapeless.{:+:, ::, CNil, Coproduct, HList, HNil, LabelledGeneric, Lazy, Typeable, Unwrapped, Witness}

trait Encoder[A] {
  def encode: Type
}

trait StructEncoder[A] extends Encoder[A] {
  override def encode: Type.Struct
}

trait UnionEncoder[A] extends Encoder[A] {
  override def encode: Type.DiscUnion
}

trait BasicEncoder[A] extends Encoder[A]

object Encoder extends EncoderInstances2

trait EncoderInstances2 extends EncoderInstances1 {
  import Type._

  implicit val stringEncoder: BasicEncoder[String] =
    pure(Str)

  implicit val intEncoder: BasicEncoder[Int] =
    pure(Num)

  implicit val doubleEncoder: BasicEncoder[Double] =
    pure(Num)

  implicit val booleanEncoder: BasicEncoder[Boolean] =
    pure(Bool)

  implicit def optionEncoder[A](implicit enc: BasicEncoder[A]): BasicEncoder[Option[A]] =
    pure(enc.encode | Null)

  implicit def seqEncoder[A](implicit enc: BasicEncoder[A]): BasicEncoder[Seq[A]] =
    pure(Array(enc.encode))

  implicit def setEncoder[A](implicit enc: BasicEncoder[A]): BasicEncoder[Set[A]] =
    pure(Array(enc.encode))

  implicit def valueClassEncoder[A <: AnyVal, B](implicit unwrapped: Unwrapped.Aux[A, B], encoder: Lazy[BasicEncoder[B]]): BasicEncoder[A] =
    pure(encoder.value.encode)
}

trait EncoderInstances1 extends EncoderInstances0 {
  import Type._

  implicit val hnilEncoder: StructEncoder[HNil] =
    pureStruct(Struct(Nil))

  implicit def hconsEncoder[K <: Symbol, H, T <: HList](
    implicit
    witness: Witness.Aux[K],
    hEnc: Lazy[BasicEncoder[H]],
    tEnc   : StructEncoder[T]
  ): StructEncoder[FieldType[K, H] :: T] = {
    val name = witness.value.name
    val head = hEnc.value.encode
    val tail = tEnc.encode
    pureStruct((name, head) +: tail)
  }

  implicit def cnilUnionEncoder: UnionEncoder[CNil] =
    pureUnion(DiscUnion(Nil))

  implicit def cconsUnionEncoder[K <: Symbol, H, T <: Coproduct](
    implicit
    witness: Witness.Aux[K],
    hEnc   : Lazy[BasicEncoder[H]],
    tEnc   : UnionEncoder[T]
  ): UnionEncoder[FieldType[K, H] :+: T] = {
    val name = witness.value.name
    val head = hEnc.value.encode
    val tail = tEnc.encode

    pureUnion((name, head) +: tail)
  }

  implicit def genericStructEncoder[A, L](implicit gen: LabelledGeneric.Aux[A, L], enc: Lazy[StructEncoder[L]]): StructEncoder[A] =
    pureStruct(enc.value.encode)

  implicit def genericUnionEncoder[A, L](implicit gen: LabelledGeneric.Aux[A, L], enc: Lazy[UnionEncoder[L]]): UnionEncoder[A] =
    pureUnion(enc.value.encode)
}

trait EncoderInstances0 extends EncoderConstructors {
  import Type._

  implicit def genericBasicEncoder[A](implicit typeable: Typeable[A]): BasicEncoder[A] =
    pure(Ref(typeable.describe))
}

trait EncoderConstructors {
  import Type._

  def apply[A](implicit enc: Encoder[A]): Encoder[A] =
    enc

  def pure[A](tpe: Type): BasicEncoder[A] =
    new BasicEncoder[A] { def encode: Type = tpe }

  def pureStruct[A](tpe: Struct): StructEncoder[A] =
    new StructEncoder[A] { def encode: Struct = tpe }

  def pureUnion[A](tpe: DiscUnion): UnionEncoder[A] =
    new UnionEncoder[A] { def encode: DiscUnion = tpe }
}