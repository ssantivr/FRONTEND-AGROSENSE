export interface Closable {
  close(): void;
}

// One stream at a time. Opening one waits for its token, and meanwhile the page may go offline, come back or leave:
// whatever was asked last wins, and an older attempt that finishes late neither opens a second stream, which would
// show every alert twice for ever, nor reports a failure nobody is waiting for.
export class LatestStream<S extends Closable> {
  private source: S | null = null;
  private generation = 0;

  async open<A>(load: () => Promise<A>, create: (access: A) => S | null): Promise<S | null> {
    this.close();
    const mine = this.generation;
    let access: A;
    try {
      access = await load();
    } catch (error) {
      if (mine !== this.generation) return null;
      throw error;
    }
    if (mine !== this.generation) return null;
    this.source = create(access);
    return this.source;
  }

  close(): void {
    this.generation += 1;
    this.source?.close();
    this.source = null;
  }
}
