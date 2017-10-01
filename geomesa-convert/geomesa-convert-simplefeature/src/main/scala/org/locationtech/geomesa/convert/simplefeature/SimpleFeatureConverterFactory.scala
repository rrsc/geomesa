package org.locationtech.geomesa.convert.simplefeature

import com.typesafe.config.Config
import org.locationtech.geomesa.convert.Transformers.Expr
import org.locationtech.geomesa.convert._
import org.locationtech.geomesa.utils.geotools.SimpleFeatureTypeLoader
import org.opengis.feature.simple.{SimpleFeature, SimpleFeatureType}

import scala.collection.immutable

class SimpleFeatureConverterFactory extends AbstractSimpleFeatureConverterFactory[SimpleFeature] {

  override protected def typeToProcess: String = "simple-feature"

  override def buildConverter(sft: SimpleFeatureType, conf: Config): SimpleFeatureConverter[SimpleFeature] = {

    val inputSFT = SimpleFeatureTypeLoader.sftForName(conf.getString("input-sft")).getOrElse(throw new RuntimeException("Cannot find input sft"))
    // TODO: does this have any implications for global params in the evaluation context?
    val idBuilder =
      if(conf.hasPath("id-field")) buildIdBuilder(conf)
      else Transformers.Col(inputSFT.getAttributeCount) // FID is put in as the last attribute, we copy it over here

    val fields = buildFields(conf, inputSFT)

    val userDataBuilder = buildUserDataBuilder(conf)
    val cacheServices = buildCacheService(conf)
    val parseOpts = getParsingOptions(conf, sft)
    buildConverter(inputSFT, sft, conf, idBuilder, fields, userDataBuilder, cacheServices, parseOpts)
  }

  override protected def buildConverter(sft: SimpleFeatureType, conf: Config, idBuilder: Expr, fields: immutable.IndexedSeq[Field], userDataBuilder: Map[String, Expr], cacheServices: Map[String, EnrichmentCache], parseOpts: ConvertParseOpts): SimpleFeatureConverter[SimpleFeature] = ???

  def buildConverter(inputSFT: SimpleFeatureType,
                     sft: SimpleFeatureType,
                     conf: Config,
                     idBuilder: Transformers.Expr,
                     fields: IndexedSeq[Field],
                     userDataBuilder: Map[String, Transformers.Expr],
                     cacheServices: Map[String, EnrichmentCache],
                     parseOpts: ConvertParseOpts): SimpleFeatureConverter[SimpleFeature] = {
    new SimpleFeatureSimpleFeatureConverter(inputSFT, sft, idBuilder, fields, userDataBuilder, cacheServices, parseOpts)
  }

  def buildFields(conf: Config, inputSFT: SimpleFeatureType): IndexedSeq[Field] = {
    import scala.collection.JavaConversions._
    val fields = conf.getConfigList("fields").map(buildField(_, inputSFT)).toIndexedSeq
    val undefined = inputSFT.getAttributeDescriptors.map(_.getLocalName).toSet.diff(fields.map(_.name).toSet)
    val defaultFields = undefined.map { f => SimpleFeatureField(f, Transformers.Col(0), f, inputSFT.indexOf(f)) }
    fields ++ defaultFields
  }

  def buildField(field: Config, inputSFT: SimpleFeatureType): Field =
    SimpleFeatureField(
      field.getString("name"),
      buildTransform(field),
      field.getString("attribute"),
      inputSFT.indexOf(field.getString("attribute"))
    )


  private def buildTransform(field: Config) = {
    if(!field.hasPath("transform")) Transformers.Col(0)
    else Transformers.parseTransform(field.getString("transform"))
  }
}

case class SimpleFeatureField(name: String, transform: Expr, attr: String, attrIdx: Int) extends Field {
  override def eval(args: Array[Any])(implicit ec: EvaluationContext): Any =
    transform.eval(Array(args(attrIdx)))
}