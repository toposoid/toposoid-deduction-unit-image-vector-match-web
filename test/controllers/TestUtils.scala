/*
 * Copyright 2021 Linked Ideal LLC.[https://linked-ideal.com/]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import com.ideal.linked.toposoid.common.{IMAGE, MANUAL, ToposoidUtils, TransversalState}
import com.ideal.linked.toposoid.knowledgebase.regist.model.{ImageReference, Knowledge, KnowledgeForImage, PropositionRelation, Reference}
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.RegistContentResult
import com.ideal.linked.toposoid.knowledgebase.model.{KnowledgeBaseNode, KnowledgeFeatureReference, LocalContext}
import com.ideal.linked.toposoid.protocol.model.base.{AnalyzedSentenceObject, AnalyzedSentenceObjects}
import com.ideal.linked.toposoid.protocol.model.neo4j.Neo4jRecords
import com.ideal.linked.toposoid.protocol.model.parser.{KnowledgeForParser, KnowledgeSentenceSetForParser}
import com.ideal.linked.toposoid.sentence.transformer.neo4j.{Neo4JUtilsImpl, Sentence2Neo4jTransformer}
import com.ideal.linked.toposoid.vectorizer.FeatureVectorizer
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.Json
import io.jvm.uuid.UUID

case class ImageBoxInfo(x:Int, y:Int, weight:Int, height:Int)

object TestUtils extends LazyLogging {

  def deleteNeo4JAllData(transversalState: TransversalState): Unit = {
    val query = "MATCH (n) OPTIONAL MATCH (n)-[r]-() DELETE n,r"
    val neo4JUtils = new Neo4JUtilsImpl()
    neo4JUtils.executeQuery(query, transversalState)
  }

  def executeQueryAndReturn(query: String, transversalState: TransversalState): Neo4jRecords = {
    val convertQuery = ToposoidUtils.encodeJsonInJson(query)
    val hoge = ToposoidUtils.decodeJsonInJson(convertQuery)
    val json = s"""{ "query":"$convertQuery", "target": "" }"""
    val jsonResult = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_GRAPHDB_WEB_HOST"), conf.getString("TOPOSOID_GRAPHDB_WEB_PORT"), "getQueryFormattedResult", transversalState)
    Json.parse(jsonResult).as[Neo4jRecords]
  }

  var usedUuidList = List.empty[String]

  def getUUID(): String = {
    var uuid: String = UUID.random.toString
    while (usedUuidList.filter(_.equals(uuid)).size > 0) {
      uuid = UUID.random.toString
    }
    usedUuidList = usedUuidList :+ uuid
    logger.info(uuid)
    uuid
  }


  def getKnowledge(lang:String, sentence: String, reference: Reference, imageBoxInfo: ImageBoxInfo, transversalState: TransversalState): Knowledge = {
    Knowledge(sentence, lang, "{}", false, List(getImageInfo(reference, imageBoxInfo, transversalState)))
  }

  def getImageInfo(reference: Reference, imageBoxInfo: ImageBoxInfo, transversalState: TransversalState): KnowledgeForImage = {
    val imageReference = ImageReference(reference: Reference, imageBoxInfo.x, imageBoxInfo.y, imageBoxInfo.weight, imageBoxInfo.height)
    val knowledgeForImage = KnowledgeForImage(id = getUUID(), imageReference = imageReference)
    val registContentResultJson = ToposoidUtils.callComponent(
      Json.toJson(knowledgeForImage).toString(),
      conf.getString("TOPOSOID_CONTENTS_ADMIN_HOST"),
      conf.getString("TOPOSOID_CONTENTS_ADMIN_PORT"),
      "registImage",
      transversalState)

    val registContentResult: RegistContentResult = Json.parse(registContentResultJson).as[RegistContentResult]
    registContentResult.knowledgeForImage
  }

  def registSingleClaim(knowledgeForParser: KnowledgeForParser, transversalState: TransversalState): Unit = {
    val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
      List.empty[KnowledgeForParser],
      List.empty[PropositionRelation],
      List(knowledgeForParser),
      List.empty[PropositionRelation])
    Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser, transversalState)
    FeatureVectorizer.createVector(knowledgeSentenceSetForParser, transversalState)
  }

  def addImageInfoToAnalyzedSentenceObjects(lang:String,inputSentence: String, knowledgeForImages: List[KnowledgeForImage], transversalState: TransversalState): String = {

    val json = lang match {
      case "ja_JP" => ToposoidUtils.callComponent(inputSentence, conf.getString("TOPOSOID_SENTENCE_PARSER_JP_WEB_HOST"), conf.getString("TOPOSOID_SENTENCE_PARSER_JP_WEB_PORT"), "analyze", transversalState)
      case "en_US" => ToposoidUtils.callComponent(inputSentence, conf.getString("TOPOSOID_SENTENCE_PARSER_EN_WEB_HOST"), conf.getString("TOPOSOID_SENTENCE_PARSER_EN_WEB_PORT"), "analyze", transversalState)
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
                  featureId = getUUID(),
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
