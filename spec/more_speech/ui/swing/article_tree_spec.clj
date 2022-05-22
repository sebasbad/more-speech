(ns more-speech.ui.swing.article-tree-spec
  (:require [speclj.core :refer :all]
            [more-speech.ui.swing.article-tree :refer :all]
            [more-speech.ui.swing.article-tree-util :refer :all]
            [more-speech.nostr.util :as util])
  (:import (javax.swing.tree DefaultMutableTreeNode)))

(describe "header tree"
  (context "finding chronological insertion point"
    (it "returns zero if empty tree"
      (let [root (DefaultMutableTreeNode.)
            event {:id 99 :created-at 1}
            event-map {99 event}
            insertion-point (find-chronological-insertion-point root 99 event-map)]
        (should= 0 insertion-point)))

    (it "returns zero if time is earlier than all events in tree"
      (let [root (DefaultMutableTreeNode.)
            child-id 1
            child (DefaultMutableTreeNode. child-id)
            _ (.add ^DefaultMutableTreeNode root child)
            event {:id 99 :created-at 1}
            event-map {99 event
                       child-id {:created-at 10}}
            insertion-point (find-chronological-insertion-point root 99 event-map)]
        (should= 0 insertion-point)))

    (it "returns 1 when event is later than only event in tree"
      (let [root (DefaultMutableTreeNode.)
            child-id 1
            child (DefaultMutableTreeNode. child-id)
            _ (.add ^DefaultMutableTreeNode root child)
            event {:id 99 :created-at 20}
            event-map {99 event
                       child-id {:created-at 10}}
            insertion-point (find-chronological-insertion-point root 99 event-map)]
        (should= 1 insertion-point))
      )

    (it "returns n when event is later than n events in tree"
      (let [root (DefaultMutableTreeNode.)
            child-id 1
            child-1 (DefaultMutableTreeNode. child-id)
            child-2 (DefaultMutableTreeNode. (+ 1 child-id))
            child-3 (DefaultMutableTreeNode. (+ 2 child-id))
            _ (.add ^DefaultMutableTreeNode root child-1)
            _ (.add ^DefaultMutableTreeNode root child-2)
            _ (.add ^DefaultMutableTreeNode root child-3)
            event {:id 99 :created-at 20}
            event-map {99 event
                       child-id {:created-at 10}
                       (+ 1 child-id) {:created-at 10}
                       (+ 2 child-id) {:created-at 10}}
            insertion-point (find-chronological-insertion-point root 99 event-map)]
        (should= 3 insertion-point)))

    (it "returns chronological insertion point"
      (let [root (DefaultMutableTreeNode.)
            child-id 1
            child-1 (DefaultMutableTreeNode. child-id)
            child-2 (DefaultMutableTreeNode. (+ 1 child-id))
            child-3 (DefaultMutableTreeNode. (+ 2 child-id))
            _ (.add ^DefaultMutableTreeNode root child-1)
            _ (.add ^DefaultMutableTreeNode root child-2)
            _ (.add ^DefaultMutableTreeNode root child-3)
            event {:id 99 :created-at 25}
            event-map {99 event
                       child-id {:created-at 10}
                       (+ 1 child-id) {:created-at 20}
                       (+ 2 child-id) {:created-at 30}}
            insertion-point (find-chronological-insertion-point root 99 event-map)]
        (should= 2 insertion-point))
      )

    (it "returns chronological insertion point when coincident"
      (let [root (DefaultMutableTreeNode.)
            child-id 1
            child-1 (DefaultMutableTreeNode. child-id)
            child-2 (DefaultMutableTreeNode. (+ 1 child-id))
            child-3 (DefaultMutableTreeNode. (+ 2 child-id))
            _ (.add ^DefaultMutableTreeNode root child-1)
            _ (.add ^DefaultMutableTreeNode root child-2)
            _ (.add ^DefaultMutableTreeNode root child-3)
            event {:id 99 :created-at 20}
            event-map {99 event
                       child-id {:created-at 10}
                       (+ 1 child-id) {:created-at 20}
                       (+ 2 child-id) {:created-at 30}}
            insertion-point (find-chronological-insertion-point root 99 event-map)]
        (should= 1 insertion-point))
      )
    )

  (context "adding references to tree nodes"
    (it "adds no node reference if the event has no references"
      (let [id 1N
            node-map {id []}
            ui-context (atom {:node-map node-map})
            event {:id id :tags []}]
        (add-references ui-context event)
        (should= [] (get-in @ui-context [:node-map id])))
      )

    (it "adds a node reference, and a tree element if the event has a reference to an existing event"
      (let [parent-id 2N
            id 1N
            parent-node (DefaultMutableTreeNode. parent-id)
            node-map {parent-id [parent-node]
                      id []}
            ui-context (atom {:node-map node-map})
            event {:id id :tags [[:e (util/num32->hex-string parent-id)]]}
            _ (add-references ui-context event)
            nodes (get-in @ui-context [:node-map id])]
        (should= 1 (count nodes))
        (should= id (.getUserObject (first nodes)))
        (should= 1 (.getChildCount parent-node))
        (should= id (.getUserObject ^DefaultMutableTreeNode (.getChildAt parent-node 0)))
        )
      )

    (it "adds a orphan if the event has a reference to an event that doesn't exist"
      (let [parent-id 2N
            id 1N
            node-map {id []}
            ui-context (atom {:node-map node-map})
            event {:id id :tags [[:e (util/num32->hex-string parent-id)]]}
            _ (add-references ui-context event)
            nodes (get-in @ui-context [:node-map id])]
        (should= 0 (count nodes))
        (should= {parent-id [id]} (:orphaned-references @ui-context))
        )
      )
    )

  (context "resolving orphaned references"
    (it "has nothing to do if the event is not an orphan"
      (let [event-id 1N
            node-map {}
            orphaned-references {}
            ui-context (atom {:node-map node-map
                              :orphaned-references orphaned-references})]
        (resolve-any-orphans ui-context event-id)
        (should= {} (:node-map @ui-context))
        (should= {} (:orphaned-references @ui-context)))
      )

    (it "resolves a parent event with a single orphan"
      (let [parent-id 1N
            orphan-id 2N
            node-map {parent-id [(DefaultMutableTreeNode. parent-id)]
                      orphan-id []}
            orphaned-references {parent-id [orphan-id]}
            ui-context (atom {:node-map node-map
                              :orphaned-references orphaned-references})
            _ (resolve-any-orphans ui-context parent-id)
            parent-nodes (get-in @ui-context [:node-map parent-id])
            parent-node (first parent-nodes)
            ]
        (should= 1 (count parent-nodes))
        (should= 1 (.getChildCount parent-node))
        (should= orphan-id (.getUserObject (.getChildAt parent-node 0)))
        (should= [(.getChildAt parent-node 0)] (get-in @ui-context [:node-map orphan-id]))
        (should= nil (get-in ui-context [:orphaned-references parent-id]))
        )
      )
    )

  (context "finding nodes"
    (it "finds nothing in an empty tree"
      (let [root (DefaultMutableTreeNode.)
            found-node (find-header-node root 1)]
        (should-be-nil found-node)))

    (it "finds nothing in non-empty tree"
      (let [root (DefaultMutableTreeNode.)
            child (DefaultMutableTreeNode. 2)
            _ (.add ^DefaultMutableTreeNode root child)
            found-node (find-header-node root 1)]
        (should-be-nil found-node)))

    (it "finds node in one-node tree"
      (let [root (DefaultMutableTreeNode.)
            child (DefaultMutableTreeNode. 1)
            _ (.add ^DefaultMutableTreeNode root child)
            found-node (find-header-node root 1)]
        (should-not-be-nil found-node)
        (should= 1 (.getUserObject found-node))))

    (it "finds node in multi-node non-branching tree"
      (let [root (DefaultMutableTreeNode.)
            child1 (DefaultMutableTreeNode. 1)
            child2 (DefaultMutableTreeNode. 2)
            child3 (DefaultMutableTreeNode. 3)
            _ (.add ^DefaultMutableTreeNode root child1)
            _ (.add ^DefaultMutableTreeNode root child2)
            _ (.add ^DefaultMutableTreeNode root child3)
            found-node (find-header-node root 2)]
        (should-not-be-nil found-node)
        (should= 2 (.getUserObject found-node))))

    (it "does not find node in multi-node non-branching tree"
      (let [root (DefaultMutableTreeNode.)
            child1 (DefaultMutableTreeNode. 1)
            child2 (DefaultMutableTreeNode. 2)
            child3 (DefaultMutableTreeNode. 3)
            _ (.add ^DefaultMutableTreeNode root child1)
            _ (.add ^DefaultMutableTreeNode root child2)
            _ (.add ^DefaultMutableTreeNode root child3)
            found-node (find-header-node root 4)]
        (should-be-nil found-node)))

    (it "finds node in multi-node branching tree"
      (let [root (DefaultMutableTreeNode.)
            child1 (DefaultMutableTreeNode. 1)
            child2 (DefaultMutableTreeNode. 2)
            child3 (DefaultMutableTreeNode. 3)
            _ (.add ^DefaultMutableTreeNode root child1)
            _ (.add ^DefaultMutableTreeNode child1 child2)
            _ (.add ^DefaultMutableTreeNode root child3)
            found-node (find-header-node root 2)]
        (should-not-be-nil found-node)
        (should= 2 (.getUserObject found-node))))

    (it "Does not find node in multi-node branching tree"
      (let [root (DefaultMutableTreeNode.)
            child1 (DefaultMutableTreeNode. 1)
            child2 (DefaultMutableTreeNode. 2)
            child3 (DefaultMutableTreeNode. 3)
            _ (.add ^DefaultMutableTreeNode root child1)
            _ (.add ^DefaultMutableTreeNode child1 child2)
            _ (.add ^DefaultMutableTreeNode root child3)
            found-node (find-header-node root 4)]
        (should-be-nil found-node)))

    ))