/*
 * Copyright (C) 2025  Linked Ideal LLC.[https://linked-ideal.com/]
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package controllers

import com.ideal.linked.toposoid.common.{SentenceType, ScopeType, FeatureType, TRANSVERSAL_STATE, ToposoidUtils, TransversalState}
import com.ideal.linked.toposoid.knowledgebase.model.{KnowledgeBaseEdge, KnowledgeBaseNode}
import com.ideal.linked.toposoid.protocol.model.base.{AnalyzedSentenceObject, AnalyzedSentenceObjects, CoveredPropositionEdge, CoveredPropositionNode, KnowledgeBaseSideInfo, MatchedFeatureInfo}
import com.ideal.linked.toposoid.protocol.model.neo4j.Neo4jRecords
import com.ideal.linked.toposoid.vectorizer.FeatureVectorizer
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.{FeatureVectorSearchResult, SingleFeatureVectorForSearch}
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.{Json, __}

import javax.inject._
import play.api._
import play.api.mvc._
import play.api.libs.json.JsValue

import scala.util.{Failure, Success, Try}
import com.ideal.linked.toposoid.protocol.model.base.DeductionResult
import com.ideal.linked.toposoid.common.Neo4JUtilsImpl
import com.ideal.linked.toposoid.protocol.model.base.VerifyingEdges
import com.ideal.linked.toposoid.common.DeductionUtils
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import com.ideal.linked.toposoid.common.RelationMatchState
import com.ideal.linked.toposoid.protocol.model.base.MatchedKnowledgeNode
import com.ideal.linked.toposoid.knowledgebase.model.KnowledgeFeatureReference
import com.ideal.linked.common.DeploymentConverter.conf

/*
sealed abstract class RelationMatchState(val index: Int)
final case object MATCHED_SOURCE_NODE_ONLY extends RelationMatchState(0)
final case object MATCHED_TARGET_NODE_ONLY extends RelationMatchState(1)
final case object NOT_MATCHED extends RelationMatchState(2)
*/

class HomeController @Inject()(val controllerComponents: ControllerComponents) extends BaseController /*with DeductionUnitController*/ with LazyLogging {
  def execute():Action[JsValue] = Action(parse.json[JsValue])  { request =>
    val transversalState = Json.parse(request.headers.get(TRANSVERSAL_STATE .str).get).as[TransversalState]
    try {
      val json = request.body
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(json.toString).as[AnalyzedSentenceObjects]
      val asos: List[AnalyzedSentenceObject] = analyzedSentenceObjects.analyzedSentenceObjects
      /*
      //Check if the image exists on asos here　or not.
      if (getAnalyzedSentenceObjectsWithImage(asos).size > 0) {
        val result: List[AnalyzedSentenceObject] = asos.foldLeft(List.empty[AnalyzedSentenceObject]) {
          (acc, x) => acc :+ analyze(x, acc, "image-vector-match", List(FeatureType.IMAGE.index), transversalState)
        }
        logger.info(ToposoidUtils.formatMessageForLogger("deduction completed.", transversalState.userId))
        Ok(Json.toJson(AnalyzedSentenceObjects(result))).as(JSON)
      }else{
        logger.info(ToposoidUtils.formatMessageForLogger("deduction skipped[No Images].", transversalState.userId))
        Ok(Json.toJson(analyzedSentenceObjects)).as(JSON)
      }
      */
      val result:List[VerifyingEdges] = asos.foldLeft(List.empty[VerifyingEdges]){
        (acc, aso) => {          
          acc :+ VerifyingEdges(
            propositionId = aso.knowledgeBaseSemiGlobalNode.propositionId,
            sentenceId = aso.knowledgeBaseSemiGlobalNode.sentenceId,
            //coveredPropositionEdges = analyzeGraphKnowledge(DeductionUtils.getUnsettledEdges(aso), aso, transversalState)
            coveredPropositionEdges = analyzeGraphKnowledge(DeductionUtils.getUnsettledEdges(aso), aso, transversalState)
          )
        }
      }
      logger.info(ToposoidUtils.formatMessageForLogger("Synonym edge analysis completed.", transversalState.userId))    
      Ok(Json.toJson(result)).as(JSON)      
    } catch {
      case e: Exception => {
        logger.error(ToposoidUtils.formatMessageForLogger(e.toString, transversalState.userId), e)
        BadRequest(Json.obj("status" -> "Error", "message" -> e.toString()))
      }
    }
  }

  private def analyzeEdge(edge:KnowledgeBaseEdge, aso:AnalyzedSentenceObject, transversalState:TransversalState):Option[CoveredPropositionEdge] = {
    val nodeMap: Map[String, KnowledgeBaseNode] =  aso.nodeMap    
    val deductionResult:DeductionResult = aso.deductionResult
    val neo4JUtils = Neo4JUtilsImpl()
    val sourceKey = edge.sourceId
    val targetKey = edge.destinationId
    val sourceNode = nodeMap.get(sourceKey).get.asInstanceOf[KnowledgeBaseNode]
    val destinationNode = nodeMap.get(targetKey).get.asInstanceOf[KnowledgeBaseNode]
    val deductionUnitName = conf.getString("TOPOSOID_DEDUCTION_UNIT_NAME")

    //sentenceIdも絞り込めるがどうするか？  
    val coveredPropositionEdges = aso.deductionResult.coveredPropositionEdges.filter(x => {
      x.sourceNode.terminalId.equals(sourceKey) && x.destinationNode.terminalId.equals(targetKey)
    })

    if(coveredPropositionEdges.size == 0) {
      None
    }else{
      val coveredPropositionEdge = coveredPropositionEdges.head
      val nodeType: String = ToposoidUtils.getNodeType(SentenceType.CLAIM.index, ScopeType.LOCAL.index, FeatureType.PREDICATE_ARGUMENT.index)
    
      if(coveredPropositionEdge.sourceNode.isConfirmed && !coveredPropositionEdge.destinationNode.isConfirmed){
        Option(coveredPropositionEdge)
      }else if(!coveredPropositionEdge.sourceNode.isConfirmed && coveredPropositionEdge.destinationNode.isConfirmed){
        Option(coveredPropositionEdge)
      }else if(!coveredPropositionEdge.sourceNode.isConfirmed && !coveredPropositionEdge.destinationNode.isConfirmed){
        Option(coveredPropositionEdge)
      }else{
        Option(coveredPropositionEdge)
      }
    }
  }

  private def analyzeGraphKnowledge(edges: List[KnowledgeBaseEdge], aso:AnalyzedSentenceObject, transversalState:TransversalState):List[CoveredPropositionEdge] = {
    
    val futures: List[Future[Option[CoveredPropositionEdge]]] = edges.foldLeft(List.empty[Future[Option[CoveredPropositionEdge]]]){
      (acc, edge) => {
        acc :+ Future(analyzeEdge(edge:KnowledgeBaseEdge, aso:AnalyzedSentenceObject, transversalState))
      }
    }    
    val combinedFuture: Future[List[Option[CoveredPropositionEdge]]] = Future.sequence(futures)
    val result = Await.result(combinedFuture, Duration.Inf)    
    result.flatten
  }

  /*
  def analyzeGraphKnowledge(edge: KnowledgeBaseEdge, aso: AnalyzedSentenceObject, accParent: List[(KnowledgeBaseSideInfo, CoveredPropositionEdge)], transversalState:TransversalState): List[(KnowledgeBaseSideInfo, CoveredPropositionEdge)] = {

    val nodeMap: Map[String, KnowledgeBaseNode] = aso.nodeMap
    val sentenceType = aso.knowledgeBaseSemiGlobalNode.sentenceType
    val sourceKey = edge.sourceId
    val targetKey = edge.destinationId
    val sourceNode = nodeMap.get(sourceKey).get.asInstanceOf[KnowledgeBaseNode]
    val destinationNode = nodeMap.get(targetKey).get.asInstanceOf[KnowledgeBaseNode]

    val initAcc: List[(KnowledgeBaseSideInfo, CoveredPropositionEdge)] = sentenceType match {
      case SentenceType.PREMISE.index => {
        accParent ::: searchMatchRelation(sourceNode, destinationNode, edge.caseStr,  SentenceType.CLAIM.index, transversalState)
      }
      case _ => accParent
    }
    initAcc ::: searchMatchRelation(sourceNode, destinationNode, edge.caseStr, sentenceType, transversalState)

  }
  */

  private def getAnalyzedSentenceObjectsWithImage(asos: List[AnalyzedSentenceObject]): List[AnalyzedSentenceObject] = {
    asos.filter(x => {
      x.nodeMap.filter(y => {
        y._2.localContext.knowledgeFeatureReferences.filter(z => {
          z.featureType == FeatureType.IMAGE.index
        }).size > 0
      }).size > 0
    })
  }

  private def getSimilarImage(node:KnowledgeBaseNode,sentenceType:Int, transversalState:TransversalState):List[String] = {
    //There may be multiple image nodes, so check them all
    node.localContext.knowledgeFeatureReferences.foldLeft(List.empty[String]){(acc, x) => {

      val vector = FeatureVectorizer.getImageVector(x.url, transversalState)
      val json: String = Json.toJson(SingleFeatureVectorForSearch(vector = vector.vector, num = conf.getString("TOPOSOID_IMAGE_VECTORDB_SEARCH_NUM_MAX").toInt)).toString()
      val featureVectorSearchResultJson: String = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_PORT"), "search", transversalState)
      val result = Json.parse(featureVectorSearchResultJson).as[FeatureVectorSearchResult]
      acc ::: result.ids.filter(_.sentenceType == sentenceType).map(_.featureId)
    }}
  }

  /**
   * This function searches for a subgraph that matches the predicate argument analysis result of the input sentence.
   *
   * @param sourceNode
   * @param targetNode
   * @param caseName
   * @return
   */
  /*
  private def searchMatchRelation(sourceNode: KnowledgeBaseNode, targetNode: KnowledgeBaseNode, caseName: String, sentenceType: Int, transversalState:TransversalState): List[(KnowledgeBaseSideInfo, CoveredPropositionEdge)] = {

    val nodeType: String = ToposoidUtils.getNodeType(sentenceType, ScopeType.LOCAL.index, FeatureType.PREDICATE_ARGUMENT.index)
    val sourceSurface = sourceNode.predicateArgumentStructure.surface
    val targetSurface = targetNode.predicateArgumentStructure.surface
    //エッジの両側ノードで厳格に一致するものがあるかどうか
    val queryBoth = "MATCH (n1:%s)-[e]-(n2:%s) WHERE n1.normalizedName='%s' AND n1.isDenialWord='%s' AND e.caseName='%s' AND n2.normalizedName='%s' AND n2.isDenialWord='%s' RETURN n1, e, n2".format(nodeType, nodeType, sourceNode.predicateArgumentStructure.normalizedName, sourceNode.predicateArgumentStructure.isDenialWord, caseName, targetNode.predicateArgumentStructure.normalizedName, targetNode.predicateArgumentStructure.isDenialWord)
    logger.debug(queryBoth)
    val queryBothResultJson: String = getCypherQueryResult(queryBoth, "", transversalState)
    if (!queryBothResultJson.equals("""{"records":[]}""")) {
      //ヒットするものがある場合
      getKnowledgeBaseSideInfo(Json.parse(queryBothResultJson).as[Neo4jRecords], sourceNode, targetNode)
    } else {
      //ヒットするものがない場合
      //上記でヒットしない場合、エッジの片側ノード（Source）で厳格に一致するものがあるかどうか
      val querySourceOnly = "MATCH (n1:%s)-[e]-(n2:%s) WHERE n1.normalizedName='%s' AND n1.isDenialWord='%s' AND e.caseName='%s' RETURN n1, e, n2".format(nodeType, nodeType, sourceNode.predicateArgumentStructure.normalizedName, sourceNode.predicateArgumentStructure.isDenialWord, caseName)
      logger.debug(querySourceOnly)
      val querySourceOnlyResultJson: String = getCypherQueryResult(querySourceOnly, "", transversalState)
      if (!querySourceOnlyResultJson.equals("""{"records":[]}""")) {
        //TargetをImageに置き換えられる可能性あり
        checkImageNode(sourceNode, targetNode, caseName, MATCHED_SOURCE_NODE_ONLY, sentenceType, List.empty[String], getSimilarImage(targetNode, sentenceType, transversalState) , transversalState)
      } else {
        //上記でヒットしない場合、エッジの片側ノード（Target）で厳格に一致するものがあるかどうか
        val queryTargetOnly = "MATCH (n1:%s)-[e]-(n2:%s) WHERE e.caseName='%s' AND n2.normalizedName='%s' AND n2.isDenialWord='%s' RETURN n1, e, n2".format(nodeType, nodeType, caseName, targetNode.predicateArgumentStructure.normalizedName, targetNode.predicateArgumentStructure.isDenialWord)
        logger.debug(queryTargetOnly)
        val queryTargetOnlyResultJson: String = getCypherQueryResult(queryTargetOnly, "", transversalState)
        if (!queryTargetOnlyResultJson.equals("""{"records":[]}""")) {
          //SourceをImageに置き換えられる可能性あり
          checkImageNode(sourceNode, targetNode, caseName, MATCHED_TARGET_NODE_ONLY, sentenceType, getSimilarImage(sourceNode, sentenceType, transversalState), List.empty[String], transversalState)
        } else {
          //もしTargetとSourceをImageに置き換えられれば、OK
          checkImageNode(sourceNode, targetNode, caseName, NOT_MATCHED, sentenceType, getSimilarImage(sourceNode, sentenceType, transversalState), getSimilarImage(targetNode, sentenceType, transversalState), transversalState)
        }
      }
    }
    //return (axiomIds, searchResults)
  }
  */
  /**
   * This function gets the proposition ID contained in the result of querying Neo4J
   *
   * @param neo4jRecords
   * @param sourceKey
   * @param tragetKey
   * @return
   */
  /*
  private def getKnowledgeBaseSideInfo(neo4jRecords: Neo4jRecords, sourceProblemNode: KnowledgeBaseNode, targetProblemNode: KnowledgeBaseNode): List[(KnowledgeBaseSideInfo, CoveredPropositionEdge)] = {
    neo4jRecords.records.foldLeft(List.empty[(KnowledgeBaseSideInfo, CoveredPropositionEdge)]) {
      (acc, x) => {
        val knowledgeBaseSideInfo = x.head.value.featureNode match {
          case Some(y) => {
            KnowledgeBaseSideInfo(y.propositionId, y.sentenceId, List(MatchedFeatureInfo(y.featureId, 1)))
          }
          case _ => {
            KnowledgeBaseSideInfo(x.head.value.localNode.get.propositionId, x.head.value.localNode.get.sentenceId, List(MatchedFeatureInfo(x.head.value.localNode.get.sentenceId, 1)))
          }
        }
        val sourceNode = CoveredPropositionNode(terminalId = sourceProblemNode.nodeId, terminalSurface = sourceProblemNode.predicateArgumentStructure.surface, terminalUrl = "")
        val destinationNode = CoveredPropositionNode(terminalId = targetProblemNode.nodeId, terminalSurface = targetProblemNode.predicateArgumentStructure.surface, terminalUrl = "")
        val coveredPropositionEdge = CoveredPropositionEdge(sourceNode = sourceNode, destinationNode = destinationNode)
        acc :+ (knowledgeBaseSideInfo, coveredPropositionEdge)

      }
    }
  }
  */
  /**
   * Check if it is logically valid even if replaced with synonyms
   *
   * @param sourceNode
   * @param targetNode
   * @param caseName
   * @param relationMatchState
   * @return
   */
  /*
  private def checkImageNode(sourceNode: KnowledgeBaseNode, targetNode: KnowledgeBaseNode, caseName: String, relationMatchState: RelationMatchState, sentenceType: Int, sourceFeatureIds:List[String], targetFeatureIds:List[String], transversalState:TransversalState): List[(KnowledgeBaseSideInfo, CoveredPropositionEdge)] = {

    val nodeType: String = ToposoidUtils.getNodeType(sentenceType, ScopeType.LOCAL.index, FeatureType.PREDICATE_ARGUMENT.index)
    val query = relationMatchState match {
      case MATCHED_SOURCE_NODE_ONLY => {
        "MATCH (n1:%s)-[e]-(n2:%s)<-[ie:ImageEdge]-(in2:ImageNode) WHERE n1.normalizedName='%s' AND n1.isDenialWord='%s' AND e.caseName='%s' AND n2.isDenialWord='%s' AND in2.featureId IN %s RETURN n1, ie, in2".format(nodeType, nodeType, sourceNode.predicateArgumentStructure.normalizedName, sourceNode.predicateArgumentStructure.isDenialWord, caseName, targetNode.predicateArgumentStructure.isDenialWord, "[%s]".format(targetFeatureIds.map("'%s'".format(_)).mkString(",")))
      }
      case MATCHED_TARGET_NODE_ONLY => {
        "MATCH (in1:ImageNode)-[ie:ImageEdge]->(n1:%s)-[e]-(n2:%s) WHERE in1.featureId IN %s AND n1.isDenialWord='%s' AND e.caseName='%s' AND n2.normalizedName='%s' AND n2.isDenialWord='%s' RETURN in1, ie, n2".format(nodeType, nodeType, "[%s]".format(sourceFeatureIds.map("'%s'".format(_)).mkString(",")), sourceNode.predicateArgumentStructure.isDenialWord, caseName, targetNode.predicateArgumentStructure.normalizedName, targetNode.predicateArgumentStructure.isDenialWord)
      }
      case NOT_MATCHED => {
        "MATCH (in1:ImageNode)-[ie1:ImageEdge]->(n1:%s)-[e]-(n2:%s)<-[ie2:ImageEdge]-(in2:ImageNode) WHERE in1.featureId IN %s AND n1.isDenialWord='%s' AND e.caseName='%s' AND n2.isDenialWord='%s' AND in2.featureId IN %s RETURN in1, e, in2".format(nodeType, nodeType, "[%s]".format(sourceFeatureIds.map("'%s'".format(_)).mkString(",")), sourceNode.predicateArgumentStructure.isDenialWord, caseName, targetNode.predicateArgumentStructure.isDenialWord, "[%s]".format(targetFeatureIds.map("'%s'".format(_)).mkString(",")))
      }
    }

    val resultJson: String = getCypherQueryResult(query, "", transversalState)
    logger.debug(query)
    if (resultJson.equals("""{"records":[]}""")) {
      List.empty[(KnowledgeBaseSideInfo, CoveredPropositionEdge)]
    } else {
      getKnowledgeBaseSideInfo(Json.parse(resultJson).as[Neo4jRecords], sourceNode, targetNode)
    }
  }
  */
}