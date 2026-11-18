// Day count: shows how many days ago each idea was made.
function daysSince(when) {
  return Math.floor((Date.now() - Date.parse(when)) / 86400000);
}

sorta.commands.on("oldest", async () => {
  const page = await sorta.ideas.list({ limit: 200 });
  if (page.ideas.length === 0) {
    return { message: "No ideas yet" };
  }
  const days = Math.max(...page.ideas.map((idea) => daysSince(idea.createdAt)));
  return { message: days === 1 ? "The oldest idea is 1 day old" : `The oldest idea is ${days} days old` };
});
