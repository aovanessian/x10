# Notes on timing

### Standard X10 commands

Standard X10 command (address or function) is 2 bits start code, 4 bits house code and 5 bits unit/function (I'll refer to this sequence of 11 bits as `frame` from now on). Each bit is transmitted with its complement (except for start code which is a special marker 11 10) over a single mains cycle, taking ~183ms (11 * 1000 / 60) for the whole frame. X10 spec calls for each frame to be repeated twice with no gap, so a simple command like `A ALL_OFF` will take ~367ms. Consecutive commands need at least 3 cycles gap between them, so addressed commands like `A1 ON` will take ~783ms:
```
length      11          11	3	11          11          -> 47 bits
time        183.3	183.3	50	183.3       183.3       -> 783.3ms
data        addr	addr	gap	func        func
```
`DIM` and `BRIGHT` commands are an exception to the rule in that they do not require gaps between successive commands, so `A1 DIM 22` will transmit an address (2 frames of 11 bits), gap (3 bits) and 'dim' function 22 times (22 frames of 11 bits), taking 4450ms (4.4 seconds).

The longest standard non-dim X10 command (`A1 A2 A3 A4 A5 A6 A7 A8 A9 A10 A11 A12 A13 A14 A15 A16 ON`) will take ~7033ms (though I doubt there are controllers that allow you to select all 16 units in this fashion).

Note: X10 signalling is slower in Europe, since mains frequency there is 50Hz; same command will take 8.5 seconds.

X10 protocol states that before sending data, transmitter should listen for absense of 1s for 8, 9 or 10 half-cycles (randomly selected); if '1' is seen, it should start the listening cycle over. This adds 67 to 83ms of overhead.

### Now, just how fast can we send commands with CM11a?

CM11a cannot transmit addresses and functions together with just a 3 bit gap, so the line access delay will happen for each address and function we send (17 times for the above `A1-A16 ON` command).

There's additional overhead on top of that - for each command CM11a responds with a checksum that needs to be acknowledged before the command will be sent; serial communication happens over 4800 kbps 8N1 serial link, meaning each byte takes a ~2ms to transmit or receive.

Here is the sequence of events for `A ALL_OFF` command:
```
computer -> cm11a       function A ALL_OFF      2 bytes     4ms
cm11a -> computer       checksum                1 byte      2ms
computer -> cm11a       0x00 (proceed)          1 byte      2ms
X10 line access negotiation                                 67 ~ 83ms
X10 frame, twice                                            367ms
cm11a -> computer       0x55 (ready)            1 byte      0ms (concurrent with previous event)
```
442 to 458ms total, assuming line is clear


This is repeated twice for `A1 A ON` (884ms - module reacts after 702ms, as soon as first function frame is completely received), thrice for `A2 A3 A ON` (1326ms, modules react after 1144ms) and so on.

So the total overhead incurred for each address/function frame is 8ms delay due to slow serial and 67ms minimum for X10 line negotiation (assuming no one else is transmitting when we are sending the command). Program overhead is minimal and cannot be measured easily (< 1ms).


I noticed something peculiar about the 'dim level' sent to the interface - if 0, dim and bright commands would emit two frames; if 1, it'll emit just one! After that, the number of commands matches the dim level, so 2 -> 2, 3 -> 3 and so on.

Emitting `A1 DIM 1` commands in succession dims the light by a very small amount, and 200+ commands are needed to turn off the light completely. I think this is what's called 'micro-dimming'. Turns out you can do 'micro-addressing' and 'micro-functions' as well. Setting dim level to 1 for address results in CM11a emitting just a single 11 bit address frame; ditto for functions. This cuts the time needed for the actual X10 commands in half (11 bits instead of 22). In effect, 'dim level' is interpreted by the CM11a as the number of times each frame needs to be repeated, with 0 being special case meaning 'twice' (or perhaps 'twice with gap').

Here is the same sequence of events for `A ALL_OFF` command, this time using the non-spec single frames:
```
computer -> cm11a       function A ALL_OFF      2 bytes     4ms
cm11a -> computer       checksum                1 byte      2ms
computer -> cm11a       0x00 (proceed)          1 byte      2ms
X10 line access negotiation                                 67 to 83ms
X10 frame, once                                             183ms
cm11a -> computer       0x55 (ready)            1 byte      0ms (concurrent with previous event)
```
258 to 274ms total, assuming line is clear

This is repeated twice for `A1 A ON` (516ms), thrice for `A2 A3 A ON` (774ms) and so on.

Funnily enough, this worked and is rock-solid in my setup - all of the modules I have react to single frame commands as expected. Now, that means we can pump out commands 40% faster:
```
                        single frames           double frames (spec)
'A1 ON; A3 OFF          1032ms                  1768ms
'A1 A2 ON; A3 A4 OFF'   1548ms                  2652ms
```
Note that second command with single frames is faster than first with double frames!

Here's a table to summarize the timings as described above:
```
command     X10 spec    X10 + CM11a             single frame            single frame + CM11a
A ALL_OFF   367ms       442ms                   183ms                   258ms (1)
A1 ON       783ms       884ms                   416ms                   516ms (1)
A1..A16 ON  7033ms      7514ms                  3917ms                  4386ms (2)
A1 DIM 22   4450ms      4543ms                  4259ms                  4359ms (3)(4)
```
- (1) timed using my program
- (2) theoretical minimum; lowest timed with the program was 4390ms (4395ms typical)
- (3) theoretical minimum; lowest timed with the program was 4366ms (4372ms typical)
- (4) 'dim' command is only marginally faster, since the only difference is address frame being sent once.

So, taking all the overhead into consideration, simple commands like `A1 ON` can be issued almost twice a second (one every ~516ms).

* Benefits:
  * less than half the time transmitting over mains (more time for other transmitters to, well, transmit)
  * faster rate of commands
* Downsides:
  * we're no longer spec-compliant
  * there could be noisy environments and flaky modules that would not be reliably controlled using this scheme
  * My CM11a (IBM-branded one) reports firmware version 1; this may not work in other firmware revisions.

I think this is a hard limit; sending commands through CM11a cannot be done any faster.

### Extended X10 commands

Section about extended commands coming up (as soon as I acquire some modules that support them).

