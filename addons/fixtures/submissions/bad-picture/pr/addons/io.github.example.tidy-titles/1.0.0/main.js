// Tidy titles: trims spaces from the titles of the chosen ideas.
sorta.commands.on("tidy", async (event) => {
  let tidied = 0;
  for (const id of event.ideas) {
    const idea = await sorta.ideas.get(id);
    if (idea !== null && idea.title !== idea.title.trim()) {
      await sorta.ideas.tag(id, ["tidied"]);
      tidied += 1;
    }
  }
  return { message: tidied === 1 ? "Tagged 1 idea" : `Tagged ${tidied} ideas` };
});
