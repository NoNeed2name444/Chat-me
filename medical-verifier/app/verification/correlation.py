def collapse_correlated_groups(items):
    parent = {}
    id_to_item = {item.id: item for item in items}

    def find(x):
        parent.setdefault(x, x)
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    def union(a, b):
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[rb] = ra

    for item in items:
        find(item.id)

        if item.study_family_id:
            key = f"study:{item.study_family_id}"
            find(key)
            union(item.id, key)

        for related in item.derived_from_ids:
            if related in id_to_item:
                union(item.id, related)

    groups = {}
    for item in items:
        groups.setdefault(find(item.id), []).append(item)

    for group_items in groups.values():
        if len(group_items) <= 1:
            continue

        stable = min(item.id for item in group_items)
        group_name = f"correlated:{stable}"

        for item in group_items:
            item.independence_group = group_name

    return items
