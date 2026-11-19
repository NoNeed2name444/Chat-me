// Word count: counts the words in ideas. Copy this folder to start your own add-on.

// Words are runs of characters between spaces, tabs and line breaks.
function countWords(text) {
  const words = text.trim().split(/\s+/);
  return words[0] === "" ? 0 : words.length;
}

function wordsIn(idea, settings) {
  return countWords(idea.text) + (settings.titles ? countWords(idea.title) : 0);
}

function amount(n, one, many) {
  return `${n} ${n === 1 ? one : many}`;
}

// Every idea the add-on may read, a page of up to 200 at a time.
async function everyIdea() {
  const ideas = [];
  let cursor;
  do {
    const page = await sorta.ideas.list({ limit: 200, cursor });
    ideas.push(...page.ideas);
    cursor = page.cursor ?? undefined;
  } while (cursor);
  return ideas;
}

// The command counts the ideas the person chose, or every idea if they chose none.
sorta.commands.on("count", async (event) => {
  const settings = await sorta.settings.get();
  const ideas = event.ideas.length > 0
    ? (await Promise.all(event.ideas.map((id) => sorta.ideas.get(id)))).filter((idea) => idea !== null)
    : await everyIdea();
  if (ideas.length === 0) {
    return { message: "No ideas to count yet" };
  }
  const words = ideas.reduce((sum, idea) => sum + wordsIn(idea, settings), 0);
  return { message: `${amount(words, "word", "words")} in ${amount(ideas.length, "idea", "ideas")}` };
});

// The card view adds one line to each idea's card.
sorta.cards.on(async (idea) => {
  const settings = await sorta.settings.get();
  return { lines: [amount(wordsIn(idea, settings), "word", "words")] };
});
