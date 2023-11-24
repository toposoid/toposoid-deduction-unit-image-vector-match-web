package controllers

import com.ideal.linked.toposoid.common.{IMAGE, MANUAL, ToposoidUtils}
import com.ideal.linked.toposoid.knowledgebase.regist.model.{ImageReference, Knowledge, KnowledgeForImage, PropositionRelation, Reference}
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.RegistContentResult
import com.ideal.linked.toposoid.knowledgebase.model.{KnowledgeBaseNode, KnowledgeFeatureReference, LocalContext}
import com.ideal.linked.toposoid.protocol.model.base.{AnalyzedSentenceObject, AnalyzedSentenceObjects}
import com.ideal.linked.toposoid.protocol.model.parser.{KnowledgeForParser, KnowledgeSentenceSetForParser}
import com.ideal.linked.toposoid.sentence.transformer.neo4j.Sentence2Neo4jTransformer
import com.ideal.linked.toposoid.vectorizer.FeatureVectorizer
import play.api.libs.json.Json
import io.jvm.uuid.UUID

case class ImageBoxInfo(x:Int, y:Int, weight:Int, height:Int)

object TestUtils {
  def getKnowledge(lang:String, sentence: String, reference: Reference, imageBoxInfo: ImageBoxInfo): Knowledge = {
    Knowledge(sentence, lang, "{}", false, List(getImageInfo(reference, imageBoxInfo)))
  }

  def getImageInfo(reference: Reference, imageBoxInfo: ImageBoxInfo): KnowledgeForImage = {
    val imageReference = ImageReference(reference: Reference, imageBoxInfo.x, imageBoxInfo.y, imageBoxInfo.weight, imageBoxInfo.height)
    val knowledgeForImage = KnowledgeForImage(id = UUID.random.toString, imageReference = imageReference)
    val registContentResultJson = ToposoidUtils.callComponent(
      Json.toJson(knowledgeForImage).toString(),
      conf.getString("TOPOSOID_CONTENTS_ADMIN_HOST"),
      conf.getString("TOPOSOID_CONTENTS_ADMIN_PORT"),
      "registImage")
    val registContentResult: RegistContentResult = Json.parse(registContentResultJson).as[RegistContentResult]
    registContentResult.knowledgeForImage
  }

  def registSingleClaim(knowledgeForParser: KnowledgeForParser): Unit = {
    val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
      List.empty[KnowledgeForParser],
      List.empty[PropositionRelation],
      List(knowledgeForParser),
      List.empty[PropositionRelation])
    Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser)
    FeatureVectorizer.createVector(knowledgeSentenceSetForParser)
  }

  def addImageInfoToAnalyzedSentenceObjects(lang:String,inputSentence: String, knowledgeForImages: List[KnowledgeForImage]): String = {

    val json = lang match {
      case "ja_JP" => ToposoidUtils.callComponent(inputSentence, conf.getString("TOPOSOID_SENTENCE_PARSER_JP_WEB_HOST"), conf.getString("TOPOSOID_SENTENCE_PARSER_JP_WEB_PORT"), "analyze")
      case "en_US" => ToposoidUtils.callComponent(inputSentence, conf.getString("TOPOSOID_SENTENCE_PARSER_EN_WEB_HOST"), conf.getString("TOPOSOID_SENTENCE_PARSER_EN_WEB_PORT"), "analyze")
    }
    //val json = ToposoidUtils.callComponent(inputSentence, conf.getString("TOPOSOID_SENTENCE_PARSER_JP_WEB_HOST"), conf.getString("TOPOSOID_SENTENCE_PARSER_JP_WEB_PORT"), "analyze")
    val asos: AnalyzedSentenceObjects = Json.parse(json).as[AnalyzedSentenceObjects]
    val updatedAsos = asos.analyzedSentenceObjects.foldLeft(List.empty[AnalyzedSentenceObject]) {
      (acc, x) => {
        val nodeMap = x.nodeMap.foldLeft(Map.empty[String, KnowledgeBaseNode]) {
          (acc2, y) => {
            val compatibleImages = knowledgeForImages.filter(z => {
              z.imageReference.reference.surface == y._2.predicateArgumentStructure.surface && z.imageReference.reference.surfaceIndex == y._2.predicateArgumentStructure.currentId
            })
            val knowledgeFeatureReferences = compatibleImages.foldLeft(List.empty[KnowledgeFeatureReference]) {
              (acc3, z) => {
                acc3 :+ KnowledgeFeatureReference(
                  propositionId = y._2.propositionId,
                  sentenceId = y._2.sentenceId,
                  featureId = UUID.random.toString,
                  featureType = IMAGE.index,
                  url = z.imageReference.reference.url,
                  source = z.imageReference.reference.originalUrlOrReference,
                  featureInputType = MANUAL.index,
                  extentText = "{}")
              }
            }
            val knowledgeBaseNode = KnowledgeBaseNode(
              nodeId = y._2.nodeId,
              propositionId = y._2.propositionId,
              sentenceId = y._2.sentenceId,
              predicateArgumentStructure = y._2.predicateArgumentStructure,
              localContext = LocalContext(
                lang = y._2.localContext.lang,
                namedEntity = y._2.localContext.namedEntity,
                rangeExpressions = y._2.localContext.rangeExpressions,
                categories = y._2.localContext.categories,
                domains = y._2.localContext.domains,
                knowledgeFeatureReferences = knowledgeFeatureReferences))
            acc2 ++ Map(y._1 -> knowledgeBaseNode)
          }
        }
        acc :+ AnalyzedSentenceObject(
          nodeMap = nodeMap,
          edgeList = x.edgeList,
          knowledgeBaseSemiGlobalNode = x.knowledgeBaseSemiGlobalNode,
          deductionResult = x.deductionResult)
      }
    }
    Json.toJson(AnalyzedSentenceObjects(updatedAsos)).toString()
  }
}
