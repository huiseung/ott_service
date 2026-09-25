import type { CollectionDetail } from "./adminCollectionApi";

export type CollectionMutationResult =
  | { status: "saved"; collection: CollectionDetail }
  | { status: "recovered"; collection: CollectionDetail; error: unknown }
  | { status: "unsynchronized"; error: unknown };

// Never replay a write after a lost response: it may already have committed.
export async function executeCollectionMutation(
  change: () => Promise<CollectionDetail>,
  reload: () => Promise<CollectionDetail>,
): Promise<CollectionMutationResult> {
  try {
    return { status: "saved", collection: await change() };
  } catch (error) {
    try {
      return { status: "recovered", collection: await reload(), error };
    } catch {
      return { status: "unsynchronized", error };
    }
  }
}
