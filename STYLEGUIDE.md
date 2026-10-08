# Styleguide

## Regions

I always liked regions, but take away the neatbeats style into a `//region (Name) [...] \n //endregion` syntax.
A reasonable goal is to always imagine that within one to one and a half screen height, once the file has collapsed regions, you can see everything in that height.
We dont want to just put the entire file into a region, that is pointless, but once there are different stuff which belong into the same file but still divide in task,
maybe a region makes sense. and for this, moving symbols so they are next to each other in logic is also worth a lot. So that f.e. all the helpers are at the bottom,
all the read methods are next to each other - you get the idea.

## Readability

Always prioritize readability over cleverness. I do like nested function chains, but ternaries are often unreadable.
Always prioritize a condition being readable. if its not, turn the condition into a variable and check on the variable,
so that we at least have named the condition.

## Architecture + Splits

A reasonable target is for a directory to never have more then 5 dirs, and more then 5 files. (both at the same time can be).
Obviously it depends, but just take this as a baseline which you can sometimes alter, but usually enforce.

If a file is over 200-300 lines, it is worth taking a look whether its too large. it doesnt have to be, but you know what i mean.
Sometimes its already unreadable before. you just have to find out.

## Abstractions

I do like abstractions when they make sense. You understand them, and everything is easier. utilizing the full kotlin scope is eagerly wanted here.

## Comments

Avoid comments. if, then really caveman talk, short, but prefer to ignore. simply kdoc comments for functions and classes are unwanted.
