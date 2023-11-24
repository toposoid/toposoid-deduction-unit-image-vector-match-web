package controllers

import com.ideal.linked.toposoid.common.{CLAIM, IMAGE, LOCAL, PREDICATE_ARGUMENT, PREMISE, ToposoidUtils}
import com.ideal.linked.toposoid.deduction.common.DeductionUnitController
import com.ideal.linked.toposoid.deduction.common.FacadeForAccessNeo4J.getCypherQueryResult
import com.ideal.linked.toposoid.knowledgebase.model.{KnowledgeBaseEdge, KnowledgeBaseNode}
import com.ideal.linked.toposoid.protocol.model.base.{AnalyzedSentenceObject, AnalyzedSentenceObjects, CoveredPropositionEdge, CoveredPropositionNode, MatchedFeatureInfo, MatchedPropositionInfo}
import com.ideal.linked.toposoid.protocol.model.neo4j.{Neo4jRecordMap, Neo4jRecords}
import com.ideal.linked.toposoid.vectorizer.FeatureVectorizer
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.{FeatureVectorSearchResult, SingleFeatureVectorForSearch}
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.{Json, __}

import javax.inject._
import play.api._
import play.api.mvc._

sealed abstract class RelationMatchState(val index: Int)
final case object MATCHED_SOURCE_NODE_ONLY extends RelationMatchState(0)
final case object MATCHED_TARGET_NODE_ONLY extends RelationMatchState(1)
final case object NOT_MATCHED extends RelationMatchState(2)

class HomeController @Inject()(val controllerComponents: ControllerComponents) extends BaseController with DeductionUnitController with LazyLogging {
  def execute() = Action(parse.json) { request =>
    try {
      val json = request.body
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(json.toString).as[AnalyzedSentenceObjects]
      val asos: List[AnalyzedSentenceObject] = analyzedSentenceObjects.analyzedSentenceObjects

      //Check if the image exists on asos here　or not.
      if (getAnalyzedSentenceObjectsWithImage(asos).size > 0) {
        val result: List[AnalyzedSentenceObject] = asos.foldLeft(List.empty[AnalyzedSentenceObject]) {
          (acc, x) => acc :+ analyze(x, acc, "image-match")
        }
        Ok(Json.toJson(AnalyzedSentenceObjects(result))).as(JSON)
      }else{
        Ok(Json.toJson(analyzedSentenceObjects)).as(JSON)
      }

    } catch {
      case e: Exception => {
        logger.error(e.toString, e)
        BadRequest(Json.obj("status" -> "Error", "message" -> e.toString()))
      }
    }
  }


  def analyzeGraphKnowledge(edge: KnowledgeBaseEdge, aso: AnalyzedSentenceObject, accParent: (List[List[Neo4jRecordMap]], List[MatchedPropositionInfo], List[CoveredPropositionEdge])): (List[List[Neo4jRecordMap]], List[MatchedPropositionInfo], List[CoveredPropositionEdge]) = {

    val nodeMap: Map[String, KnowledgeBaseNode] = aso.nodeMap
    val sentenceType = aso.knowledgeBaseSemiGlobalNode.sentenceType
    val sourceKey = edge.sourceId
    val targetKey = edge.destinationId
    val sourceNode = nodeMap.get(sourceKey).getOrElse().asInstanceOf[KnowledgeBaseNode]
    val destinationNode = nodeMap.get(targetKey).getOrElse().asInstanceOf[KnowledgeBaseNode]

    val initAcc: (List[List[Neo4jRecordMap]], List[MatchedPropositionInfo], List[CoveredPropositionEdge]) = sentenceType match {
      case PREMISE.index => {
        val (searchResults, matchedPropositionInfoList, coveredPropositionEdgeList) = searchMatchRelation(sourceNode, destinationNode, edge.caseStr, CLAIM.index)
        if (matchedPropositionInfoList.size == 0) return accParent

        (accParent._1 ::: searchResults, accParent._2 ::: matchedPropositionInfoList, accParent._3 ::: coveredPropositionEdgeList)
      }
      case _ => accParent
    }
    val (searchResults, matchedPropositionInfoList, coveredPropositionEdgeList) = searchMatchRelation(sourceNode, destinationNode, edge.caseStr, sentenceType)

    (initAcc._1 ::: searchResults, initAcc._2 ::: matchedPropositionInfoList, initAcc._3 ::: coveredPropositionEdgeList)

  }

  private def getAnalyzedSentenceObjectsWithImage(asos: List[AnalyzedSentenceObject]): List[AnalyzedSentenceObject] = {
    asos.filter(x => {
      x.nodeMap.filter(y => {
        y._2.localContext.knowledgeFeatureReferences.filter(z => {
          z.featureType == IMAGE.index
        }).size > 0
      }).size > 0
    })
  }

  private def getSimilarImage(node:KnowledgeBaseNode,sentenceType:Int):List[String] = {
    //There may be multiple image nodes, so check them all
    node.localContext.knowledgeFeatureReferences.foldLeft(List.empty[String]){(acc, x) => {

      val vector = FeatureVectorizer.getImageVector(x.url)
      val json: String = Json.toJson(SingleFeatureVectorForSearch(vector = vector.vector, num = conf.getString("TOPOSOID_IMAGE_VECTORDB_SEARCH_NUM_MAX").toInt)).toString()
      val featureVectorSearchResultJson: String = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_PORT"), "search")
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
  private def searchMatchRelation(sourceNode: KnowledgeBaseNode, targetNode: KnowledgeBaseNode, caseName: String, sentenceType: Int): (List[List[Neo4jRecordMap]], List[MatchedPropositionInfo], List[CoveredPropositionEdge]) = {

    val nodeType: String = ToposoidUtils.getNodeType(sentenceType, LOCAL.index, PREDICATE_ARGUMENT.index)
    val sourceSurface = sourceNode.predicateArgumentStructure.surface
    val targetSurface = targetNode.predicateArgumentStructure.surface
    //エッジの両側ノードで厳格に一致するものがあるかどうか
    val queryBoth = "MATCH (n1:%s)-[e]-(n2:%s) WHERE n1.normalizedName='%s' AND n1.isDenialWord='%s' AND e.caseName='%s' AND n2.normalizedName='%s' AND n2.isDenialWord='%s' RETURN n1, e, n2".format(nodeType, nodeType, sourceNode.predicateArgumentStructure.normalizedName, sourceNode.predicateArgumentStructure.isDenialWord, caseName, targetNode.predicateArgumentStructure.normalizedName, targetNode.predicateArgumentStructure.isDenialWord)
    logger.debug(queryBoth)
    val queryBothResultJson: String = getCypherQueryResult(queryBoth, "")
    if (!queryBothResultJson.equals("""{"records":[]}""")) {
      //ヒットするものがある場合
      getMatchedPropositionInfo(Json.parse(queryBothResultJson).as[Neo4jRecords], sourceNode, targetNode)
    } else {
      //ヒットするものがない場合
      //上記でヒットしない場合、エッジの片側ノード（Source）で厳格に一致するものがあるかどうか
      val querySourceOnly = "MATCH (n1:%s)-[e]-(n2:%s) WHERE n1.normalizedName='%s' AND n1.isDenialWord='%s' AND e.caseName='%s' RETURN n1, e, n2".format(nodeType, nodeType, sourceNode.predicateArgumentStructure.normalizedName, sourceNode.predicateArgumentStructure.isDenialWord, caseName)
      logger.debug(querySourceOnly)
      val querySourceOnlyResultJson: String = getCypherQueryResult(querySourceOnly, "")
      if (!querySourceOnlyResultJson.equals("""{"records":[]}""")) {
        //TargetをImageに置き換えられる可能性あり
        checkImageNode(sourceNode, targetNode, caseName, MATCHED_SOURCE_NODE_ONLY, sentenceType, List.empty[String], getSimilarImage(targetNode, sentenceType))
      } else {
        //上記でヒットしない場合、エッジの片側ノード（Target）で厳格に一致するものがあるかどうか
        val queryTargetOnly = "MATCH (n1:%s)-[e]-(n2:%s) WHERE e.caseName='%s' AND n2.normalizedName='%s' AND n2.isDenialWord='%s' RETURN n1, e, n2".format(nodeType, nodeType, caseName, targetNode.predicateArgumentStructure.normalizedName, targetNode.predicateArgumentStructure.isDenialWord)
        logger.debug(queryTargetOnly)
        val queryTargetOnlyResultJson: String = getCypherQueryResult(queryTargetOnly, "")
        if (!queryTargetOnlyResultJson.equals("""{"records":[]}""")) {
          //SourceをImageに置き換えられる可能性あり
          checkImageNode(sourceNode, targetNode, caseName, MATCHED_TARGET_NODE_ONLY, sentenceType, getSimilarImage(sourceNode, sentenceType), List.empty[String])
        } else {
          //もしTargetとSourceをImageに置き換えられれば、OK
          checkImageNode(sourceNode, targetNode, caseName, NOT_MATCHED, sentenceType, getSimilarImage(sourceNode, sentenceType), getSimilarImage(targetNode, sentenceType))
        }
      }
    }
    //return (axiomIds, searchResults)
  }

  /**
   * This function gets the proposition ID contained in the result of querying Neo4J
   *
   * @param neo4jRecords
   * @param sourceKey
   * @param tragetKey
   * @return
   */
  private def getMatchedPropositionInfo(neo4jRecords: Neo4jRecords, sourceProblemNode: KnowledgeBaseNode, targetProblemNode: KnowledgeBaseNode): (List[List[Neo4jRecordMap]], List[MatchedPropositionInfo], List[CoveredPropositionEdge]) = {
    val (searchResults, matchPropositionInfoList, coveredPropositionEdgeList) = neo4jRecords.records.foldLeft((List.empty[List[Neo4jRecordMap]], List.empty[MatchedPropositionInfo], List.empty[CoveredPropositionEdge])) {
      (acc, x) => {
        val matchPropositionInfo = x.head.value.featureNode match {
          case Some(y) => {
            MatchedPropositionInfo(y.propositionId, List(MatchedFeatureInfo(y.sentenceId, 1)))
          }
          case _ => {
            MatchedPropositionInfo(x.head.value.localNode.get.propositionId, List(MatchedFeatureInfo(x.head.value.localNode.get.sentenceId, 1)))
          }
        }
        val sourceNode = CoveredPropositionNode(terminalId = sourceProblemNode.nodeId, terminalSurface = sourceProblemNode.predicateArgumentStructure.surface, terminalUrl = "")
        val destinationNode = CoveredPropositionNode(terminalId = targetProblemNode.nodeId, terminalSurface = targetProblemNode.predicateArgumentStructure.surface, terminalUrl = "")
        val coveredPropositionEdgeList = CoveredPropositionEdge(sourceNode = sourceNode, destinationNode = destinationNode)
        (acc._1 :+ x, acc._2 :+ matchPropositionInfo, acc._3 :+ coveredPropositionEdgeList)

      }
    }
    (searchResults, matchPropositionInfoList, coveredPropositionEdgeList)
  }

  /**
   * Check if it is logically valid even if replaced with synonyms
   *
   * @param sourceNode
   * @param targetNode
   * @param caseName
   * @param relationMatchState
   * @return
   */
  private def checkImageNode(sourceNode: KnowledgeBaseNode, targetNode: KnowledgeBaseNode, caseName: String, relationMatchState: RelationMatchState, sentenceType: Int, sourceFeatureIds:List[String], targetFeatureIds:List[String]): (List[List[Neo4jRecordMap]], List[MatchedPropositionInfo], List[CoveredPropositionEdge]) = {

    val nodeType: String = ToposoidUtils.getNodeType(sentenceType, LOCAL.index, PREDICATE_ARGUMENT.index)
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

    val resultJson: String = getCypherQueryResult(query, "")
    logger.debug(query)
    if (resultJson.equals("""{"records":[]}""")) {
      (List.empty[List[Neo4jRecordMap]], List.empty[MatchedPropositionInfo], List.empty[CoveredPropositionEdge])
    } else {
      getMatchedPropositionInfo(Json.parse(resultJson).as[Neo4jRecords], sourceNode, targetNode)
    }
  }

}