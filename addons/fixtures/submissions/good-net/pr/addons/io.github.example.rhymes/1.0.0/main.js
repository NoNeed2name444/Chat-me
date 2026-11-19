// Rhymes: looks up words that rhyme with the chosen idea's title.
sorta.commands.on("rhyme", async (event) => {
  if (event.ideas.length === 0) {
    return { message: "Choose an idea first" };
  }
  const idea = await sorta.ideas.get(event.ideas[0]);
  const word = encodeURIComponent((idea?.title ?? "").split(" ")[0]);
  const reply = await sorta.net.fetch(`https://api.example.org/v1/rhymes?word=${word}`);
  return { message: reply.status === 200 ? reply.body.slice(0, 120) : "Couldn't reach the rhyme list" };
});
