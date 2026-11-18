// One stream at a time. Opening one waits for its token, and meanwhile the page may go offline, come back or leave:
// whatever was asked last wins, and an older attempt that finishes late neither opens a second stream, which would
// show every alert twice for ever, nor reports a failure nobody is waiting for.
export class LatestStream {
    source = null;
    generation = 0;
    async open(load, create) {
        this.close();
        const mine = this.generation;
        let access;
        try {
            access = await load();
        }
        catch (error) {
            if (mine !== this.generation)
                return null;
            throw error;
        }
        if (mine !== this.generation)
            return null;
        this.source = create(access);
        return this.source;
    }
    close() {
        this.generation += 1;
        this.source?.close();
        this.source = null;
    }
}
