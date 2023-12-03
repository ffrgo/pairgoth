onLoad(() => {
  $('input.numeric').imask({
    mask: Number,
    scale: 0,
    min: 0,
    max: 4000
  });
  //$('')
});
